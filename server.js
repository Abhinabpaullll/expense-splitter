const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { DatabaseSync } = require('node:sqlite');

const PORT = process.env.PORT || 3000;
const DB_PATH = path.join(os.homedir(), '.expensesplitter', 'expensesplitter.db');

// Ensure db directory exists
const dbDir = path.dirname(DB_PATH);
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
}

let db;
try {
  db = new DatabaseSync(DB_PATH);
  db.exec('PRAGMA foreign_keys = ON;');
  console.log('Connected to SQLite database at:', DB_PATH);
} catch (e) {
  console.error('Failed to open SQLite database:', e);
}

// Password utility matching Java PBKDF2WithHmacSHA256, 120,000 iterations, 32 bytes
function hashPassword(password, saltBase64) {
  const salt = Buffer.from(saltBase64, 'base64');
  return crypto.pbkdf2Sync(password, salt, 120000, 32, 'sha256').toString('base64');
}

function verifyPassword(password, saltBase64, expectedHash) {
  const computed = hashPassword(password, saltBase64);
  return crypto.timingSafeEqual(Buffer.from(computed), Buffer.from(expectedHash));
}

function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  });
  res.end(JSON.stringify(data));
}

// Greedy min-cash-flow debt simplification algorithm (matches DebtSimplificationService.java)
function computeSimplifiedDebts(allMembers, expenses, payers, splits, payments) {
  const balances = new Map();
  for (const m of allMembers) {
    balances.set(m.id, 0);
  }

  for (const e of expenses) {
    const ePayers = payers.filter(p => p.expense_id === e.id);
    const eSplits = splits.filter(s => s.expense_id === e.id);
    for (const p of ePayers) {
      balances.set(p.member_id, (balances.get(p.member_id) || 0) + Number(p.amount_cents));
    }
    for (const s of eSplits) {
      balances.set(s.member_id, (balances.get(s.member_id) || 0) - Number(s.share_cents));
    }
  }

  for (const p of payments) {
    const amt = Number(p.amount_cents);
    balances.set(p.from_member_id, (balances.get(p.from_member_id) || 0) + amt);
    balances.set(p.to_member_id, (balances.get(p.to_member_id) || 0) - amt);
  }

  const creditors = [];
  const debtors = [];
  for (const [mId, cents] of balances.entries()) {
    if (cents > 0) creditors.push({ memberId: mId, amountCents: cents });
    else if (cents < 0) debtors.push({ memberId: mId, amountCents: -cents });
  }

  creditors.sort((a, b) => b.amountCents - a.amountCents);
  debtors.sort((a, b) => b.amountCents - a.amountCents);

  const transfers = [];
  let ci = 0;
  let di = 0;
  while (ci < creditors.length && di < debtors.length) {
    const c = creditors[ci];
    const d = debtors[di];
    const settle = Math.min(c.amountCents, d.amountCents);
    if (settle > 0) {
      transfers.push({
        fromMemberId: d.memberId,
        toMemberId: c.memberId,
        amountCents: settle,
        amount: settle / 100
      });
    }
    c.amountCents -= settle;
    d.amountCents -= settle;
    if (c.amountCents <= 0) ci++;
    if (d.amountCents <= 0) di++;
  }
  return transfers;
}

function getState() {
  if (!db) return {};

  const users = db.prepare('SELECT id, display_name, email, created_at, last_login_at FROM users ORDER BY created_at ASC').all();
  const settingsRows = db.prepare('SELECT key, value FROM settings').all();
  const settings = {};
  for (const r of settingsRows) {
    settings[r.key] = r.value;
  }

  const activeUserId = settings['last_signed_in_user_id'] || null;
  let activeUser = null;
  let activeMember = null;
  if (activeUserId) {
    activeUser = users.find(u => u.id === activeUserId) || null;
    if (activeUser) {
      activeMember = db.prepare('SELECT * FROM members WHERE user_id = ?').get(activeUserId) || null;
    }
  }

  // Groups visible to active user (or all if none logged in)
  let groups = [];
  if (activeUserId) {
    groups = db.prepare(`
      SELECT DISTINCT g.* FROM groups g
      LEFT JOIN group_members gm ON g.id = gm.group_id
      LEFT JOIN members m ON gm.member_id = m.id
      WHERE g.created_by_user_id = ? OR m.user_id = ?
      ORDER BY g.updated_at DESC
    `).all(activeUserId, activeUserId);
  } else {
    groups = db.prepare('SELECT * FROM groups ORDER BY updated_at DESC').all();
  }

  const allMembers = db.prepare('SELECT * FROM members ORDER BY name ASC').all();
  const groupMembers = db.prepare('SELECT * FROM group_members').all();
  const expenses = db.prepare('SELECT * FROM expenses ORDER BY date DESC, created_at DESC').all();
  const payers = db.prepare('SELECT * FROM expense_payers').all();
  const splits = db.prepare('SELECT * FROM expense_splits').all();
  const payments = db.prepare('SELECT * FROM payments ORDER BY date DESC, created_at DESC').all();

  // Compute metrics for active member
  let owedCents = 0;
  let oweCents = 0;
  const currentMemberId = activeMember ? activeMember.id : null;

  if (currentMemberId) {
    for (const exp of expenses) {
      const expPayers = payers.filter(p => p.expense_id === exp.id);
      const expSplits = splits.filter(s => s.expense_id === exp.id);

      const paidByUser = expPayers.filter(p => p.member_id === currentMemberId)
        .reduce((sum, p) => sum + Number(p.amount_cents), 0);
      const splitUser = expSplits.filter(s => s.member_id === currentMemberId)
        .reduce((sum, s) => sum + Number(s.share_cents), 0);

      const net = paidByUser - splitUser;
      if (net > 0) owedCents += net;
      else if (net < 0) oweCents += Math.abs(net);
    }

    // Include payments
    for (const p of payments) {
      const amt = Number(p.amount_cents);
      if (p.to_member_id === currentMemberId) {
        owedCents = Math.max(0, owedCents - amt);
      } else if (p.from_member_id === currentMemberId) {
        oweCents = Math.max(0, oweCents - amt);
      }
    }
  }

  const netCents = owedCents - oweCents;
  // Determine currency symbol from settings
  const rawCurrency = settings['default_currency'] || settings['currency'] || '';
  let currencySymbol = '₹'; // default
  if (rawCurrency) {
    if (rawCurrency.includes('USD') || rawCurrency === '$') currencySymbol = '$';
    else if (rawCurrency.includes('EUR') || rawCurrency === '€') currencySymbol = '€';
    else if (rawCurrency.includes('GBP') || rawCurrency === '£') currencySymbol = '£';
    else if (rawCurrency.includes('INR') || rawCurrency === '₹') currencySymbol = '₹';
    else if (rawCurrency.length <= 3) currencySymbol = rawCurrency; // pass through raw symbol
  }

  const theme = settings['theme'] || 'light';

  // Format expenses list with payer name and group name
  const memberMap = new Map(allMembers.map(m => [m.id, m.name]));
  const groupMap = new Map(groups.map(g => [g.id, g.name]));

  const formattedExpenses = expenses.map(e => {
    const ePayers = payers.filter(p => p.expense_id === e.id);
    const payerName = ePayers.length > 0 ? (memberMap.get(ePayers[0].member_id) || 'Member') : 'Unknown';
    const groupName = groupMap.get(e.group_id) || 'General';

    let userShareCents = 0;
    if (currentMemberId) {
      const eSplits = splits.filter(s => s.expense_id === e.id && s.member_id === currentMemberId);
      userShareCents = eSplits.reduce((acc, s) => acc + Number(s.share_cents), 0);
      const userPaid = ePayers.filter(p => p.member_id === currentMemberId).reduce((acc, p) => acc + Number(p.amount_cents), 0);
      userShareCents = userPaid - userShareCents;
    }

    const amt = Number(e.amount_cents) / 100;
    return {
      id: e.id,
      groupId: e.group_id,
      group_id: e.group_id,
      groupName: groupName,
      group_name: groupName,
      title: e.title,
      description: e.title,
      amount: amt,
      category: e.category,
      date: e.date,
      paidBy: payerName,
      payer_name: payerName,
      userNet: userShareCents / 100,
      user_share: userShareCents / 100,
      status: 'Pending'
    };
  });

  // Groups formatted with total_spent and user_balance
  const formattedGroups = groups.map(g => {
    const gMemIds = groupMembers.filter(gm => gm.group_id === g.id).map(gm => gm.member_id);
    const gMems = allMembers.filter(m => gMemIds.includes(m.id));
    const memberNames = gMems.map(m => m.name);

    // Compute total spent in group
    const groupExpList = expenses.filter(e => e.group_id === g.id);
    const totalSpentCents = groupExpList.reduce((sum, e) => sum + Number(e.amount_cents), 0);

    // Compute active member balance in this group
    let userGroupBalanceCents = 0;
    if (currentMemberId) {
      for (const ge of groupExpList) {
        const ePayers = payers.filter(p => p.expense_id === ge.id && p.member_id === currentMemberId);
        const eSplits = splits.filter(s => s.expense_id === ge.id && s.member_id === currentMemberId);
        const paid = ePayers.reduce((acc, p) => acc + Number(p.amount_cents), 0);
        const owed = eSplits.reduce((acc, s) => acc + Number(s.share_cents), 0);
        userGroupBalanceCents += (paid - owed);
      }
      for (const p of payments.filter(p => p.group_id === g.id)) {
        if (p.to_member_id === currentMemberId) {
          userGroupBalanceCents -= Number(p.amount_cents);
        } else if (p.from_member_id === currentMemberId) {
          userGroupBalanceCents += Number(p.amount_cents);
        }
      }
    }

    return {
      id: g.id,
      name: g.name,
      currency: g.currency,
      status: g.status,
      membersCount: gMems.length,
      member_count: gMems.length,
      member_names: memberNames,
      members: gMems,
      total_spent: totalSpentCents / 100,
      user_balance: userGroupBalanceCents / 100
    };
  });

  return {
    user: activeUser ? {
      id: activeUser.id,
      name: activeUser.display_name,
      email: activeUser.email || '',
      avatar: (activeUser.display_name.split(' ').map(n => n[0]).join('') || '?').substring(0, 2).toUpperCase(),
      memberId: currentMemberId
    } : null,
    users: users.map(u => ({ id: u.id, name: u.display_name, email: u.email })),
    currency: currencySymbol,
    theme: theme,
    metrics: {
      owed: owedCents / 100,
      owe: oweCents / 100,
      net: netCents / 100
    },
    groups: formattedGroups,
    members: allMembers,
    expenses: formattedExpenses,
    transfers: computeSimplifiedDebts(allMembers, expenses, payers, splits, payments).map(t => ({
      fromMemberId: t.fromMemberId,
      fromName: memberMap.get(t.fromMemberId) || 'Member',
      toMemberId: t.toMemberId,
      toName: memberMap.get(t.toMemberId) || 'Member',
      amount: t.amount,
      isUserDebtor: t.fromMemberId === currentMemberId,
      isUserCreditor: t.toMemberId === currentMemberId
    })),
    settlements: payments.map(p => ({
      id: p.id,
      groupId: p.group_id,
      from: memberMap.get(p.from_member_id) || 'Member',
      from_name: memberMap.get(p.from_member_id) || 'Member',
      to: memberMap.get(p.to_member_id) || 'Member',
      to_name: memberMap.get(p.to_member_id) || 'Member',
      amount: Number(p.amount_cents) / 100,
      method: p.payment_method,
      reference: p.notes,
      date: p.date,
      notes: p.notes
    }))
  };
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml'
};

const server = http.createServer(async (req, res) => {
  // CORS preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end();
    return;
  }

  const [reqPath] = req.url.split('?');

  // API Endpoints
  if (reqPath === '/api/state' && req.method === 'GET') {
    return sendJson(res, 200, getState());
  }

  if (reqPath === '/api/auth/register' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { name, email, password } = body;
      if (!name || !name.trim()) {
        return sendJson(res, 400, { error: 'Please enter your full name' });
      }
      if (!password || password.length < 6) {
        return sendJson(res, 400, { error: 'Password must be at least 6 characters' });
      }

      if (email && email.trim()) {
        const existing = db.prepare('SELECT id FROM users WHERE lower(email) = lower(?)').get(email.trim());
        if (existing) {
          return sendJson(res, 400, { error: 'An account with this email already exists. Please Sign In instead.' });
        }
      }

      const salt = crypto.randomBytes(16).toString('base64');
      const hash = hashPassword(password, salt);
      const userId = crypto.randomUUID();
      const now = Date.now();

      db.prepare(`
        INSERT INTO users (id, display_name, email, password_hash, salt, created_at, last_login_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).run(userId, name.trim(), email ? email.trim() : null, hash, salt, now, now);

      // Create matching member
      const memberId = crypto.randomUUID();
      db.prepare(`
        INSERT INTO members (id, name, email, phone, user_id, created_at)
        VALUES (?, ?, ?, null, ?, ?)
      `).run(memberId, name.trim(), email ? email.trim() : null, userId, now);

      // Set active session
      db.prepare(`
        INSERT INTO settings (key, value) VALUES ('last_signed_in_user_id', ?)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value
      `).run(userId);

      return sendJson(res, 200, { success: true, user: { id: userId, name: name.trim() } });
    } catch (e) {
      console.error('Registration error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/auth/login' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { userId, email, password } = body;
      let user = null;
      if (userId) {
        user = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
      } else if (email) {
        user = db.prepare('SELECT * FROM users WHERE lower(email) = lower(?)').get(email.trim());
      }

      if (!user) {
        return sendJson(res, 404, { error: 'Account not found' });
      }

      if (!verifyPassword(password, user.salt, user.password_hash)) {
        return sendJson(res, 401, { error: 'Invalid password' });
      }

      db.prepare(`
        INSERT INTO settings (key, value) VALUES ('last_signed_in_user_id', ?)
        ON CONFLICT(key) DO UPDATE SET value = excluded.value
      `).run(user.id);

      db.prepare('UPDATE users SET last_login_at = ? WHERE id = ?').run(Date.now(), user.id);

      return sendJson(res, 200, { success: true, user: { id: user.id, name: user.display_name } });
    } catch (e) {
      console.error('Login error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/auth/logout' && req.method === 'POST') {
    try {
      db.prepare("DELETE FROM settings WHERE key = 'last_signed_in_user_id'").run();
      return sendJson(res, 200, { success: true });
    } catch (e) {
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/settings' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { key, value } = body;
      if (key) {
        db.prepare(`
          INSERT INTO settings (key, value) VALUES (?, ?)
          ON CONFLICT(key) DO UPDATE SET value = excluded.value
        `).run(key, String(value));
      }
      return sendJson(res, 200, { success: true });
    } catch (e) {
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/groups' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { name, currency, members } = body;
      if (!name || !name.trim()) return sendJson(res, 400, { error: 'Group name is required' });

      const settings = db.prepare("SELECT value FROM settings WHERE key = 'last_signed_in_user_id'").get();
      const activeUserId = settings ? settings.value : null;
      let activeMemberId = null;
      if (activeUserId) {
        const m = db.prepare('SELECT id FROM members WHERE user_id = ?').get(activeUserId);
        if (m) {
          activeMemberId = m.id;
        } else {
          const u = db.prepare('SELECT display_name, email FROM users WHERE id = ?').get(activeUserId);
          if (u) {
            activeMemberId = crypto.randomUUID();
            db.prepare(`
              INSERT INTO members (id, name, email, phone, user_id, created_at)
              VALUES (?, ?, ?, null, ?, ?)
            `).run(activeMemberId, u.display_name, u.email, activeUserId, Date.now());
          }
        }
      }

      const groupId = crypto.randomUUID();
      const now = Date.now();
      db.prepare(`
        INSERT INTO groups (id, name, currency, status, created_by_user_id, created_at, updated_at)
        VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
      `).run(groupId, name.trim(), currency || 'INR', activeUserId, now, now);

      if (activeMemberId) {
        db.prepare(`
          INSERT INTO group_members (group_id, member_id) VALUES (?, ?)
        `).run(groupId, activeMemberId);
      }

      // Add additional initial members if specified
      let memberList = [];
      if (Array.isArray(members)) {
        memberList = members;
      } else if (typeof members === 'string' && members.trim()) {
        memberList = members.split(',').map(s => s.trim()).filter(Boolean);
      }

      for (const rawName of memberList) {
        const memName = String(rawName).trim();
        if (!memName) continue;
        let existing = db.prepare('SELECT id FROM members WHERE lower(name) = lower(?)').get(memName);
        let mId;
        if (existing) {
          mId = existing.id;
        } else {
          mId = crypto.randomUUID();
          db.prepare(`
            INSERT INTO members (id, name, email, phone, user_id, created_at)
            VALUES (?, ?, null, null, null, ?)
          `).run(mId, memName, Date.now());
        }
        db.prepare('INSERT OR IGNORE INTO group_members (group_id, member_id) VALUES (?, ?)').run(groupId, mId);
      }

      return sendJson(res, 200, { success: true, id: groupId });
    } catch (e) {
      console.error('Create group error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/groups/member' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { groupId, name, email } = body;
      if (!groupId || !name) return sendJson(res, 400, { error: 'GroupId and name required' });

      // Check if user already exists
      let existingMember = null;
      if (email) {
        const u = db.prepare('SELECT id FROM users WHERE lower(email) = lower(?)').get(email.trim());
        if (u) {
          existingMember = db.prepare('SELECT * FROM members WHERE user_id = ?').get(u.id);
        }
      }

      let memberId;
      if (existingMember) {
        memberId = existingMember.id;
      } else {
        memberId = crypto.randomUUID();
        db.prepare(`
          INSERT INTO members (id, name, email, phone, user_id, created_at)
          VALUES (?, ?, ?, null, null, ?)
        `).run(memberId, name.trim(), email ? email.trim() : null, Date.now());
      }

      db.prepare('INSERT OR IGNORE INTO group_members (group_id, member_id) VALUES (?, ?)').run(groupId, memberId);
      return sendJson(res, 200, { success: true, memberId });
    } catch (e) {
      console.error('Add member error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/expenses' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const groupId = body.groupId || body.group_id;
      const title = (body.title || body.description || 'Expense').trim();
      const amount = Number(body.amount);
      const category = body.category || 'General';
      const payerId = body.payerId || body.payer_member_id;
      const memberSplits = body.splits || [];
      const totalCents = Math.round(amount * 100);

      const expId = crypto.randomUUID();
      const now = Date.now();
      const dateVal = body.date || new Date().toISOString().split('T')[0];

      db.exec('BEGIN TRANSACTION;');
      try {
        db.prepare(`
          INSERT INTO expenses (id, group_id, title, amount_cents, category, date, split_type, notes, created_at)
          VALUES (?, ?, ?, ?, ?, ?, 'EQUAL', '', ?)
        `).run(expId, groupId, title, totalCents, category, dateVal, now);

        db.prepare(`
          INSERT INTO expense_payers (id, expense_id, member_id, amount_cents)
          VALUES (?, ?, ?, ?)
        `).run(crypto.randomUUID(), expId, payerId, totalCents);

        if (Array.isArray(memberSplits) && memberSplits.length > 0) {
          const insertSplit = db.prepare(`
            INSERT INTO expense_splits (id, expense_id, member_id, share_cents, exact_param)
            VALUES (?, ?, ?, ?, 0)
          `);
          for (const s of memberSplits) {
            const mId = s.memberId || s.member_id;
            const sAmt = s.share != null ? s.share : (s.amount != null ? s.amount : 0);
            insertSplit.run(crypto.randomUUID(), expId, mId, Math.round(Number(sAmt) * 100));
          }
        }

        db.exec('COMMIT;');
        return sendJson(res, 200, { success: true, id: expId });
      } catch (err) {
        db.exec('ROLLBACK;');
        throw err;
      }
    } catch (e) {
      console.error('Add expense error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  if (reqPath === '/api/payments' && req.method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      let groupId = body.groupId || body.group_id;
      const fromMemberId = body.fromMemberId || body.payer_member_id;
      const toMemberId = body.toMemberId || body.payee_member_id;
      const amount = Number(body.amount);
      const method = body.method || 'UPI';
      const amountCents = Math.round(amount * 100);

      if (!groupId) {
        // Find common group
        const g = db.prepare(`
          SELECT gm1.group_id FROM group_members gm1
          JOIN group_members gm2 ON gm1.group_id = gm2.group_id
          WHERE gm1.member_id = ? AND gm2.member_id = ?
          LIMIT 1
        `).get(fromMemberId, toMemberId);
        if (g) {
          groupId = g.group_id;
        } else {
          // fallback to any group the payer belongs to
          const fallback = db.prepare('SELECT group_id FROM group_members WHERE member_id = ? LIMIT 1').get(fromMemberId);
          groupId = fallback ? fallback.group_id : null;
        }
      }

      if (!groupId) {
        return sendJson(res, 400, { error: 'Group is required for settlement recording' });
      }

      const paymentId = crypto.randomUUID();
      const today = new Date().toISOString().split('T')[0];
      db.prepare(`
        INSERT INTO payments (id, group_id, from_member_id, to_member_id, amount_cents, payment_method, date, notes, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'Settlement payment', ?)
      `).run(paymentId, groupId, fromMemberId, toMemberId, amountCents, method, today, Date.now());

      return sendJson(res, 200, { success: true, id: paymentId });
    } catch (e) {
      console.error('Record payment error:', e);
      return sendJson(res, 500, { error: e.message });
    }
  }

  // Static File Serving
  let filePath = path.join(__dirname, reqPath === '/' ? 'index.html' : reqPath);

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('404 Not Found');
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, () => {
  console.log(`Expense Splitter Server listening on http://localhost:${PORT}`);
});
