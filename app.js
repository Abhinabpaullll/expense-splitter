/**
 * Expense Splitter — Master Application & State Engine
 * API-driven: reads from /api/state, writes via REST endpoints.
 * No localStorage, no hardcoded dummy data.
 */

(function () {
  'use strict';

  // --- Application State (fetched from server) ---
  let state = {
    user: null,
    currency: '$',
    theme: 'light',
    metrics: { owed: 0, owe: 0, net: 0 },
    expenses: [],
    groups: [],
    settlements: [],
    members: []
  };

  // --- Fetch State from API ---
  async function fetchState() {
    try {
      const res = await fetch('/api/state');
      if (!res.ok) throw new Error('Failed to fetch state: ' + res.status);
      const data = await res.json();
      state = data;
      applyTheme(state.theme || 'light');
      renderAll();
    } catch (err) {
      console.error('fetchState error:', err);
      showToast('Failed to load data from server', 'error');
    }
  }

  // --- Apply theme ---
  function applyTheme(theme) {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
    updateThemeIcon();
  }

  // --- Render all views from state ---
  function renderAll() {
    const loggedIn = !!(state.user && state.user.id);
    if (loggedIn) {
      window.hideAuthOverlay();
      renderUserProfile(state.user);
      renderMetrics(state.metrics, state.currency);
      renderExpenseTable(state.expenses, state.currency, state.user);
      renderGroups(state.groups, state.currency);
      renderGroupSummaryCards(state.groups, state.metrics);
      renderSettleUpView(state.transfers, state.settlements, state.currency, state.user, state.metrics);
      renderReminders(state.expenses, state.user, state.currency);
      renderReportsView(state.expenses, state.groups, state.currency);
      renderGroupsDropdown(state.groups);
      renderMembersInModal(state.members, state.user);
      renderRecordModalSelects(state.members, state.user);
      renderCategoryChart(state.expenses, state.currency);
      updateHeaderScope();
    } else {
      renderUserProfile(null);
      window.showAuthOverlay('login');
    }
  }

  // --- Auth Overlay & Account Switching ---
  function showAuthError(msg) {
    const errorBox = document.getElementById('auth-error-msg');
    const errorText = document.getElementById('auth-error-text');
    if (errorBox && errorText) {
      errorText.textContent = msg;
      errorBox.classList.remove('hidden');
    }
  }

  function clearAuthError() {
    const errorBox = document.getElementById('auth-error-msg');
    if (errorBox) errorBox.classList.add('hidden');
  }

  function populateAuthUserSelect() {
    const box = document.getElementById('auth-quick-user-box');
    const select = document.getElementById('auth-user-select');
    if (!box || !select) return;
    const users = state.users || [];
    if (users.length === 0) {
      box.classList.add('hidden');
      return;
    }
    box.classList.remove('hidden');
    select.innerHTML = '<option value="">-- Quick select existing account --</option>' +
      users.map(u => `<option value="${escAttr(u.email || u.id)}">${escHtml(u.name || 'User')} (${escHtml(u.email || 'No email')})</option>`).join('');
    select.onchange = function() {
      const emailInput = document.getElementById('login-email');
      if (emailInput && select.value) {
        emailInput.value = select.value;
        const passInput = document.getElementById('login-password');
        if (passInput) passInput.focus();
      }
    };
  }

  window.switchAuthTab = function (tab) {
    clearAuthError();
    const loginForm = document.getElementById('login-form');
    const registerForm = document.getElementById('register-form');
    const showLoginBtn = document.getElementById('auth-show-login');
    const showRegisterBtn = document.getElementById('auth-show-register');

    if (tab === 'register') {
      if (loginForm) loginForm.classList.add('hidden');
      if (registerForm) registerForm.classList.remove('hidden');
      if (showRegisterBtn) {
        showRegisterBtn.className = 'flex-1 py-1.5 text-center rounded-md font-semibold text-on-surface bg-surface-container-lowest shadow-sm transition-all';
      }
      if (showLoginBtn) {
        showLoginBtn.className = 'flex-1 py-1.5 text-center rounded-md text-secondary hover:text-on-surface transition-all';
      }
      setTimeout(() => {
        const nameInput = document.getElementById('register-name');
        if (nameInput) nameInput.focus();
      }, 50);
    } else {
      if (loginForm) loginForm.classList.remove('hidden');
      if (registerForm) registerForm.classList.add('hidden');
      if (showLoginBtn) {
        showLoginBtn.className = 'flex-1 py-1.5 text-center rounded-md font-semibold text-on-surface bg-surface-container-lowest shadow-sm transition-all';
      }
      if (showRegisterBtn) {
        showRegisterBtn.className = 'flex-1 py-1.5 text-center rounded-md text-secondary hover:text-on-surface transition-all';
      }
      setTimeout(() => {
        const emailInput = document.getElementById('login-email');
        if (emailInput) emailInput.focus();
      }, 50);
    }
  };

  window.showAuthOverlay = function (tab = 'login') {
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.classList.remove('hidden');
    window.switchAuthTab(tab);
    populateAuthUserSelect();

    const closeBtn = document.getElementById('auth-close-btn');
    if (closeBtn) {
      if (state.user && state.user.id) {
        closeBtn.classList.remove('hidden');
      } else {
        closeBtn.classList.add('hidden');
      }
    }
  };

  window.hideAuthOverlay = function () {
    const overlay = document.getElementById('auth-overlay');
    if (overlay) overlay.classList.add('hidden');
  };

  window.openAuthModal = function (tab = 'login') {
    window.showAuthOverlay(tab);
  };

  // --- Render User Profile (sidebar footer) ---
  function renderUserProfile(user) {
    const avatarEl = document.getElementById('sidebar-user-avatar');
    const nameEl = document.getElementById('sidebar-user-name');
    const emailEl = document.getElementById('sidebar-user-email');
    const signOutBtn = document.getElementById('btn-sign-out');

    if (!user) {
      if (avatarEl) avatarEl.textContent = '--';
      if (nameEl) nameEl.textContent = 'Sign In';
      if (emailEl) emailEl.textContent = 'Click to authenticate';
      if (signOutBtn) signOutBtn.classList.add('hidden');
      return;
    }
    const initials = (user.name || 'U').split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
    if (avatarEl) avatarEl.textContent = initials;
    if (nameEl) nameEl.textContent = user.name || '';
    if (emailEl) emailEl.textContent = user.email || '';
    if (signOutBtn) signOutBtn.classList.remove('hidden');
  }

  // --- Render Metric Cards ---
  function renderMetrics(metrics, currency) {
    const owed = metrics.owed || 0;
    const owe = metrics.owe || 0;
    const net = metrics.net || 0;

    const owedEl = document.getElementById('metric-owed');
    const oweEl = document.getElementById('metric-owe');
    const netEl = document.getElementById('metric-net');
    const netLabelEl = document.getElementById('metric-net-label');
    const owedCountEl = document.getElementById('metric-owed-count');
    const oweCountEl = document.getElementById('metric-owe-count');
    const netStatusEl = document.getElementById('metric-net-status');

    if (owedEl) owedEl.textContent = '+' + currency + owed.toFixed(2);
    if (oweEl) oweEl.textContent = '-' + currency + owe.toFixed(2);

    if (netEl) {
      const sign = net >= 0 ? '+' : '-';
      netEl.textContent = sign + currency + Math.abs(net).toFixed(2);
      netEl.className = netEl.className.replace(/text-(error|tertiary)/, net >= 0 ? 'text-tertiary' : 'text-error');
    }

    const owedPending = (state.expenses || []).filter(e => e.payer_name === (state.user && state.user.name) && e.status === 'Pending').length;
    const owePending = (state.expenses || []).filter(e => e.payer_name !== (state.user && state.user.name) && e.status === 'Pending').length;

    if (owedCountEl) owedCountEl.textContent = owedPending + ' active credit' + (owedPending !== 1 ? 's' : '');
    if (oweCountEl) oweCountEl.textContent = owePending + ' pending debit' + (owePending !== 1 ? 's' : '');

    if (netStatusEl) netStatusEl.textContent = net >= 0 ? 'Surplus' : 'Deficit';
    if (netLabelEl) netLabelEl.textContent = net >= 0 ? 'Settlement ready' : 'Payment due';
  }

  // --- Format date from ISO string ---
  function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }

  // --- Render Expense Table (dashboard) ---
  function renderExpenseTable(expenses, currency, user) {
    const tbody = document.querySelector('#ledger-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!expenses || expenses.length === 0) {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td colspan="6" class="px-space-md py-8 text-center text-secondary font-body-default">
        No expenses yet. Add your first expense to get started.
      </td>`;
      tbody.appendChild(tr);
      updateLedgerFooter(0, 0);
      return;
    }

    const userName = user && user.name;
    expenses.forEach(exp => {
      const tr = document.createElement('tr');
      tr.className = 'h-[44px] hover:bg-surface-container-low transition-colors group';

      const isPayer = exp.payer_name === userName;
      const share = exp.user_share != null ? exp.user_share : 0;
      const isPositive = share > 0; // user is owed (they paid)
      const shareColor = isPositive ? 'text-tertiary' : (share < 0 ? 'text-error' : 'text-secondary');
      const shareSign = isPositive ? '+' : (share < 0 ? '-' : '');
      const statusBg = exp.status === 'Settled'
        ? 'bg-surface-container-high text-on-surface-variant'
        : 'bg-tertiary-fixed text-on-tertiary-fixed';

      tr.innerHTML = `
        <td class="px-space-md font-tabular-body text-tabular-body text-secondary whitespace-nowrap">${formatDate(exp.date)}</td>
        <td class="px-space-md font-medium text-on-surface whitespace-nowrap">${escHtml(exp.description)}</td>
        <td class="px-space-md whitespace-nowrap">
          <span class="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-caption text-caption">${escHtml(exp.group_name || '')}</span>
        </td>
        <td class="px-space-md text-on-surface-variant whitespace-nowrap">${escHtml(exp.payer_name || '')}</td>
        <td class="px-space-md font-tabular-body text-tabular-body font-medium ${shareColor} text-right whitespace-nowrap">
          ${shareSign}${currency}${Math.abs(share).toFixed(2)}
        </td>
        <td class="px-space-md text-center whitespace-nowrap">
          <span class="inline-flex items-center px-2 py-0.5 rounded text-caption font-medium ${statusBg}">
            ${escHtml(exp.status || 'Pending')}
          </span>
        </td>
      `;
      tbody.appendChild(tr);
    });

    updateLedgerFooter(expenses.length, expenses.length);
  }

  function updateLedgerFooter(shown, total) {
    const footerEl = document.getElementById('ledger-footer-count');
    if (footerEl) footerEl.textContent = `Showing ${shown} of ${total} record${total !== 1 ? 's' : ''}`;
  }

  // --- Render Groups Table ---
  function renderGroups(groups, currency) {
    const tbody = document.querySelector('#groups-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!groups || groups.length === 0) {
      const tr = document.createElement('tr');
      tr.innerHTML = `<td colspan="5" class="px-space-md py-12 text-center text-secondary font-body-default">
        <div class="flex flex-col items-center justify-center gap-3 max-w-sm mx-auto">
          <div class="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-primary">
            <span class="material-symbols-outlined text-[26px]">group_add</span>
          </div>
          <div>
            <p class="font-semibold text-on-surface text-base">No groups yet</p>
            <p class="text-xs text-secondary mt-1">Create your first group to start recording and splitting expenses with others.</p>
          </div>
          <button onclick="openNewGroupModal()" type="button" class="mt-2 h-9 px-4 rounded-lg bg-primary hover:bg-primary-container text-on-primary font-medium text-sm inline-flex items-center gap-2 transition-all shadow-sm cursor-pointer">
            <span class="material-symbols-outlined text-[18px]">add</span>
            <span>Create First Group</span>
          </button>
        </div>
      </td>`;
      tbody.appendChild(tr);
      return;
    }

    groups.forEach(g => {
      const tr = document.createElement('tr');
      tr.className = 'h-[44px] hover:bg-surface-container-low/70 transition-colors group';
      const balance = g.user_balance || 0;
      const balColor = balance > 0 ? 'text-tertiary-container' : (balance < 0 ? 'text-error' : 'text-secondary');
      const balSign = balance > 0 ? '+' : (balance < 0 ? '-' : '');
      const dotColor = balance > 0 ? 'bg-tertiary-container' : (balance < 0 ? 'bg-error' : 'bg-secondary');
      const membersText = g.member_names ? g.member_names.join(', ') : '';
      const memberCount = g.member_count || (g.member_names ? g.member_names.length : 0);

      tr.innerHTML = `
        <td class="px-space-md font-label-md text-label-md text-on-surface">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full ${dotColor} shrink-0"></span>
            <span class="truncate font-semibold text-on-surface">${escHtml(g.name)}</span>
          </div>
        </td>
        <td class="px-space-md font-body-default text-body-default text-on-surface-variant">
          <span class="font-tabular-body text-tabular-body">${memberCount} member${memberCount !== 1 ? 's' : ''}</span>
          ${membersText ? `<span class="text-secondary ml-1.5">(${escHtml(membersText)})</span>` : ''}
        </td>
        <td class="px-space-md font-tabular-body text-tabular-body text-on-surface text-right">
          ${currency}${(g.total_spent || 0).toFixed(2)}
        </td>
        <td class="px-space-md font-tabular-body text-tabular-body ${balColor} text-right font-medium">
          ${balSign}${currency}${Math.abs(balance).toFixed(2)}
        </td>
        <td class="px-space-md text-right">
          <a class="font-label-md text-label-md text-primary-container hover:underline inline-flex items-center gap-1 cursor-pointer"
             onclick="window.navigate('group-detail'); window.loadGroupDetail('${g.id}')">
            <span>View details</span>
            <span class="material-symbols-outlined text-[16px]">chevron_right</span>
          </a>
        </td>
      `;
      tbody.appendChild(tr);
    });

    // Update footer aggregates
    const totalSpent = groups.reduce((s, g) => s + (g.total_spent || 0), 0);
    const netPos = groups.reduce((s, g) => s + (g.user_balance || 0), 0);
    const footerAgg = document.getElementById('groups-footer-aggregate');
    const footerNet = document.getElementById('groups-footer-net');
    if (footerAgg) footerAgg.textContent = currency + totalSpent.toFixed(2);
    if (footerNet) footerNet.textContent = (netPos >= 0 ? '+' : '-') + currency + Math.abs(netPos).toFixed(2);
  }

  // --- Render Group Summary Mini-Cards (on groups view top) ---
  function renderGroupSummaryCards(groups, metrics) {
    const activeCount = (groups || []).filter(g => g.status !== 'Archived').length;
    const total = (groups || []).length;
    const totalSpent = (groups || []).reduce((s, g) => s + (g.total_spent || 0), 0);
    const netPos = metrics.net || 0;
    const outstandingOwe = metrics.owe || 0;

    const activeEl = document.getElementById('groups-stat-active');
    const totalEl = document.getElementById('groups-stat-total');
    const turnoverEl = document.getElementById('groups-stat-turnover');
    const netEl = document.getElementById('groups-stat-net');
    const oweEl = document.getElementById('groups-stat-owe');

    if (activeEl) activeEl.textContent = activeCount;
    if (totalEl) totalEl.textContent = 'of ' + total + ' total';
    if (turnoverEl) turnoverEl.textContent = (state.currency || '$') + totalSpent.toFixed(2);
    if (netEl) netEl.textContent = (netPos >= 0 ? '+' : '-') + (state.currency || '$') + Math.abs(netPos).toFixed(2);
    if (oweEl) oweEl.textContent = '-' + (state.currency || '$') + outstandingOwe.toFixed(2);
  }

  // --- Render Settle Up View (Debts & Settlements) ---
  function renderSettleUpView(transfers, settlements, currency, user, metrics) {
    const oweEl = document.getElementById('settle-stat-owe');
    const receiveEl = document.getElementById('settle-stat-receive');
    const transfersCountEl = document.getElementById('settle-stat-transfers');
    const compressionEl = document.getElementById('settle-stat-compression');

    const owe = metrics?.owe || 0;
    const owed = metrics?.owed || 0;
    const transferList = transfers || [];

    if (oweEl) oweEl.textContent = '-' + currency + owe.toFixed(2);
    if (receiveEl) receiveEl.textContent = '+' + currency + owed.toFixed(2);
    if (transfersCountEl) transfersCountEl.textContent = transferList.length;
    if (compressionEl) compressionEl.textContent = transferList.length === 0 ? 'Optimal (0)' : 'Active Flow';

    // Render transfer cards (Greedy Debt Simplification)
    const transferBox = document.getElementById('settle-up-transfers');
    if (transferBox) {
      transferBox.innerHTML = '';
      if (transferList.length === 0) {
        transferBox.innerHTML = `
          <div class="py-8 text-center text-secondary font-body-default flex flex-col items-center gap-2">
            <span class="material-symbols-outlined text-[32px] text-tertiary">check_circle</span>
            <p class="font-medium text-on-surface">All debts are fully settled!</p>
            <p class="text-xs text-secondary">No pending transfers are required between members.</p>
          </div>
        `;
      } else {
        const currentMemberId = user?.memberId;
        transferList.forEach(t => {
          const isMePayer = t.fromMemberId === currentMemberId;
          const isMePayee = t.toMemberId === currentMemberId;
          const card = document.createElement('div');
          card.className = `p-space-md rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-colors ${
            isMePayer ? 'bg-error-container/10 border-error/30' : (isMePayee ? 'bg-tertiary-container/10 border-tertiary/30' : 'bg-surface-container-low border-outline-variant/40')
          }`;

          const fromInitial = (t.fromName || 'M')[0].toUpperCase();
          const toInitial = (t.toName || 'M')[0].toUpperCase();

          card.innerHTML = `
            <div class="flex items-center gap-3">
              <div class="flex items-center -space-x-2 shrink-0">
                <div class="w-8 h-8 rounded-full bg-error-container text-on-error-container flex items-center justify-center text-xs font-bold border-2 border-surface">
                  ${escHtml(fromInitial)}
                </div>
                <div class="w-8 h-8 rounded-full bg-tertiary-container text-on-tertiary-container flex items-center justify-center text-xs font-bold border-2 border-surface">
                  ${escHtml(toInitial)}
                </div>
              </div>
              <div>
                <p class="font-medium text-on-surface text-sm">
                  <span class="font-semibold">${escHtml(t.fromName)}</span>
                  ${isMePayer ? '<span class="text-xs text-error font-bold ml-1">(You)</span>' : ''}
                  <span class="text-secondary font-normal mx-1">pays</span>
                  <span class="font-semibold">${escHtml(t.toName)}</span>
                  ${isMePayee ? '<span class="text-xs text-tertiary font-bold ml-1">(You)</span>' : ''}
                </p>
                <p class="text-xs text-secondary">Simplified direct settlement transfer</p>
              </div>
            </div>
            <div class="flex items-center gap-3 self-end sm:self-auto">
              <span class="font-tabular-summary text-base font-bold text-on-surface">
                ${currency}${(t.amount || 0).toFixed(2)}
              </span>
              <button onclick="window.openRecordModal('${t.fromMemberId}', '${t.toMemberId}', ${t.amount})"
                      class="h-8 px-3 rounded-lg bg-primary hover:bg-primary-container text-on-primary text-xs font-medium inline-flex items-center gap-1.5 transition-all shadow-sm">
                <span class="material-symbols-outlined text-[16px]">payments</span>
                <span>Settle Now</span>
              </button>
            </div>
          `;
          transferBox.appendChild(card);
        });
      }
    }

    // Render past recorded settlements
    const historyBox = document.getElementById('settlements-list');
    if (historyBox) {
      historyBox.innerHTML = '';
      if (!settlements || settlements.length === 0) {
        historyBox.innerHTML = `
          <div class="py-6 text-center text-secondary text-sm font-body-default">
            No past settlements recorded yet.
          </div>
        `;
      } else {
        settlements.forEach(s => {
          const div = document.createElement('div');
          div.className = 'flex items-center justify-between p-space-sm rounded-lg bg-surface-container-low hover:bg-surface-container transition-colors';
          div.innerHTML = `
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-full bg-surface-container text-on-surface flex items-center justify-center shrink-0 text-xs font-semibold">
                ${escHtml((s.from_name || '?')[0].toUpperCase())}
              </div>
              <div>
                <p class="font-label-md text-sm text-on-surface">
                  <span class="font-semibold">${escHtml(s.from_name || '')}</span>
                  <span class="text-secondary font-normal mx-1">paid</span>
                  <span class="font-semibold">${escHtml(s.to_name || '')}</span>
                </p>
                <p class="text-xs text-secondary">${formatDate(s.date)} · ${escHtml(s.method || 'Cash')} ${s.notes ? '· ' + escHtml(s.notes) : ''}</p>
              </div>
            </div>
            <span class="font-tabular-body text-sm font-semibold text-tertiary">
              ${currency}${(s.amount || 0).toFixed(2)}
            </span>
          `;
          historyBox.appendChild(div);
        });
      }
    }
  }

  // --- Render Reports View ---
  let reportFilterGroup = '';
  let reportSearchQuery = '';

  function renderReportsView(expenses, groups, currency) {
    const groupSel = document.getElementById('report-group-filter');
    if (groupSel) {
      const currentVal = groupSel.value;
      groupSel.innerHTML = '<option value="">All Groups (Consolidated)</option>' +
        (groups || []).map(g => `<option value="${escAttr(g.id)}">${escHtml(g.name)}</option>`).join('');
      groupSel.value = currentVal;

      if (!groupSel.hasAttribute('data-wired')) {
        groupSel.setAttribute('data-wired', '1');
        groupSel.addEventListener('change', () => {
          reportFilterGroup = groupSel.value;
          updateReportTable(expenses, currency);
        });
      }
    }

    const searchInput = document.getElementById('report-search-input');
    if (searchInput && !searchInput.hasAttribute('data-wired')) {
      searchInput.setAttribute('data-wired', '1');
      searchInput.addEventListener('input', () => {
        reportSearchQuery = searchInput.value.toLowerCase().trim();
        updateReportTable(expenses, currency);
      });
    }

    updateReportTable(expenses, currency);
  }

  function updateReportTable(expenses, currency) {
    let filtered = expenses || [];
    if (reportFilterGroup) {
      filtered = filtered.filter(e => e.group_id === reportFilterGroup || e.groupId === reportFilterGroup);
    }
    if (reportSearchQuery) {
      filtered = filtered.filter(e =>
        (e.description && e.description.toLowerCase().includes(reportSearchQuery)) ||
        (e.payer_name && e.payer_name.toLowerCase().includes(reportSearchQuery)) ||
        (e.category && e.category.toLowerCase().includes(reportSearchQuery)) ||
        (e.group_name && e.group_name.toLowerCase().includes(reportSearchQuery))
      );
    }

    const totalOutlay = filtered.reduce((sum, e) => sum + (e.amount || 0), 0);
    const count = filtered.length;
    const avg = count > 0 ? (totalOutlay / count) : 0;

    const totalEl = document.getElementById('report-total-amount');
    const countEl = document.getElementById('report-transaction-count');
    const avgEl = document.getElementById('report-average-amount');
    if (totalEl) totalEl.textContent = currency + totalOutlay.toFixed(2);
    if (countEl) countEl.textContent = count;
    if (avgEl) avgEl.textContent = currency + avg.toFixed(2);

    const tbody = document.querySelector('#report-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" class="px-space-md py-8 text-center text-secondary">
        No expense transactions found matching the selected filters.
      </td></tr>`;
      return;
    }

    filtered.forEach(e => {
      const tr = document.createElement('tr');
      tr.className = 'h-[44px] hover:bg-surface-container-low transition-colors';
      tr.innerHTML = `
        <td class="px-space-md font-tabular-body text-secondary whitespace-nowrap">${formatDate(e.date)}</td>
        <td class="px-space-md font-medium text-on-surface whitespace-nowrap">${escHtml(e.description || '')}</td>
        <td class="px-space-md whitespace-nowrap">
          <span class="px-2 py-0.5 rounded-full bg-surface-container text-on-surface-variant font-caption text-xs">${escHtml(e.group_name || 'General')}</span>
        </td>
        <td class="px-space-md text-on-surface-variant whitespace-nowrap">${escHtml(e.payer_name || '')}</td>
        <td class="px-space-md text-secondary whitespace-nowrap">${escHtml(e.category || 'General')}</td>
        <td class="px-space-md font-tabular-body font-semibold text-right whitespace-nowrap">${currency}${(e.amount || 0).toFixed(2)}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // --- Render Reminders view ---
  function renderReminders(expenses, user, currency) {
    const container = document.getElementById('reminders-list');
    const badge = document.getElementById('sidebar-reminder-count');
    if (!container) return;

    const pending = (expenses || []).filter(e => e.status === 'Pending');
    if (badge) badge.textContent = pending.length;
    container.innerHTML = '';

    if (pending.length === 0) {
      container.innerHTML = `<div class="py-8 text-center text-secondary font-body-default">
        No pending reminders. All settled up!
      </div>`;
      return;
    }

    pending.forEach(e => {
      const div = document.createElement('div');
      div.className = 'flex items-center justify-between p-space-md rounded-lg bg-surface-container-lowest hover:bg-surface-container-low transition-colors';
      const share = Math.abs(e.user_share || 0);
      div.innerHTML = `
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 rounded-full bg-error-container text-on-error-container flex items-center justify-center shrink-0">
            <span class="material-symbols-outlined text-[18px]">schedule</span>
          </div>
          <div>
            <p class="font-label-md text-label-md text-on-surface font-semibold">${escHtml(e.description || '')}</p>
            <p class="font-caption text-caption text-secondary">${escHtml(e.group_name || '')} · ${formatDate(e.date)}</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <span class="font-tabular-body text-tabular-body font-semibold text-error">${currency}${share.toFixed(2)}</span>
          <button class="h-7 px-3 rounded-lg bg-surface-container text-on-surface font-label-md text-xs hover:bg-surface-container-high transition-colors"
                  onclick="window.showToast('Nudge sent for: ${escAttr(e.description)}', 'success')">
            Send Nudge
          </button>
        </div>
      `;
      container.appendChild(div);
    });
  }

  // --- Category Chart (spending by category) ---
  function renderCategoryChart(expenses, currency) {
    const chartContainer = document.getElementById('category-chart-bars');
    const totalEl = document.getElementById('category-total');
    const stackEl = document.getElementById('category-stack');
    if (!chartContainer) return;

    const catMap = {};
    let total = 0;
    (expenses || []).forEach(e => {
      const cat = e.category || 'Other';
      catMap[cat] = (catMap[cat] || 0) + (e.amount || 0);
      total += (e.amount || 0);
    });

    if (totalEl) totalEl.textContent = currency + total.toFixed(2);

    const colors = ['bg-primary', 'bg-on-surface', 'bg-secondary', 'bg-outline-variant', 'bg-tertiary'];
    const cats = Object.entries(catMap).sort((a, b) => b[1] - a[1]);

    chartContainer.innerHTML = '';
    if (stackEl) stackEl.innerHTML = '';

    if (cats.length === 0) {
      chartContainer.innerHTML = `<p class="text-caption text-secondary py-2">No category data yet.</p>`;
      return;
    }

    cats.forEach(([cat, amt], i) => {
      const pct = total > 0 ? Math.round((amt / total) * 100) : 0;
      const color = colors[i % colors.length];

      if (stackEl) {
        const seg = document.createElement('div');
        seg.className = `h-full ${color} transition-all duration-500`;
        seg.style.width = pct + '%';
        seg.title = `${cat}: ${pct}%`;
        stackEl.appendChild(seg);
      }

      const barDiv = document.createElement('div');
      barDiv.className = 'flex flex-col gap-1.5';
      barDiv.innerHTML = `
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="w-2.5 h-2.5 rounded-sm ${color}"></span>
            <span class="font-label-md text-label-md text-on-surface">${escHtml(cat)}</span>
          </div>
          <div class="flex items-center gap-3">
            <span class="font-caption text-caption text-secondary">${pct}%</span>
            <span class="font-tabular-body text-tabular-body font-semibold text-on-surface text-right w-20 font-tabular-header">${amt.toFixed(2)}</span>
          </div>
        </div>
        <div class="w-full h-2 rounded bg-surface-container-low overflow-hidden">
          <div class="h-full ${color} transition-all duration-500" style="width: ${pct}%;"></div>
        </div>
      `;
      chartContainer.appendChild(barDiv);
    });
  }

  // --- Groups Dropdown in Add Expense Modal ---
  function renderGroupsDropdown(groups) {
    const sel = document.getElementById('expense-group');
    if (!sel) return;
    const prev = sel.value;
    sel.innerHTML = '';
    (groups || []).forEach(g => {
      const opt = document.createElement('option');
      opt.value = g.id;
      opt.textContent = g.name;
      sel.appendChild(opt);
    });
    if (groups && groups.length === 0) {
      const opt = document.createElement('option');
      opt.value = '';
      opt.textContent = '— Create a group first —';
      sel.appendChild(opt);
    }
    sel.value = prev || (groups && groups.length > 0 ? groups[0].id : '');
  }

  // --- Members Checkboxes in Add Expense Modal ---
  function renderMembersInModal(members, user) {
    const container = document.getElementById('expense-members-list');
    if (!container) return;
    container.innerHTML = '';

    (members || []).forEach(m => {
      const isMe = user && m.user_id === user.id;
      const label = document.createElement('label');
      label.className = 'flex items-center justify-between py-1.5 cursor-pointer';
      label.innerHTML = `
        <div class="flex items-center gap-2">
          <input type="checkbox" class="member-check rounded" data-id="${m.id}" ${isMe ? 'checked' : 'checked'}>
          <span class="font-body-default text-body-default text-on-surface">${escHtml(m.name)}${isMe ? ' (You)' : ''}</span>
        </div>
        <input type="number" class="member-input w-20 text-right bg-surface-container-low rounded px-2 py-0.5 text-sm font-tabular-body" 
               data-member="${m.id}" value="0.00" step="0.01" min="0">
      `;
      container.appendChild(label);
    });

    // Re-wire change events
    container.querySelectorAll('.member-check').forEach(chk => {
      chk.addEventListener('change', calculateSplitShares);
    });
    container.querySelectorAll('.member-input').forEach(inp => {
      inp.addEventListener('input', updateVerificationBar);
    });

    // Payer dropdown
    const payerSel = document.getElementById('expense-payer');
    if (payerSel) {
      const prev = payerSel.value;
      payerSel.innerHTML = '';
      (members || []).forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.id;
        opt.textContent = m.name + (user && m.user_id === user.id ? ' (You)' : '');
        payerSel.appendChild(opt);
      });
      if (user) {
        const me = (members || []).find(m => m.user_id === user.id);
        if (me) payerSel.value = me.id;
      }
    }
  }

  // --- Record Settlement Modal Selects ---
  function renderRecordModalSelects(members, user) {
    const payerSel = document.getElementById('modal-payer-select');
    const payeeSel = document.getElementById('modal-payee-select');
    if (!payerSel || !payeeSel) return;

    payerSel.innerHTML = '';
    payeeSel.innerHTML = '';
    (members || []).forEach(m => {
      const label = m.name + (user && m.user_id === user.id ? ' (You)' : '');
      const opt1 = document.createElement('option');
      opt1.value = m.id;
      opt1.textContent = label;
      payerSel.appendChild(opt1);
      const opt2 = document.createElement('option');
      opt2.value = m.id;
      opt2.textContent = label;
      payeeSel.appendChild(opt2);
    });
  }

  // --- Update Header Scope ---
  function updateHeaderScope() {
    const el = document.getElementById('header-scope');
    if (el) {
      const now = new Date();
      el.textContent = now.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
    }
  }

  // --- Group Detail ---
  window.loadGroupDetail = function (groupId) {
    activeDetailGroupId = groupId;
    const group = (state.groups || []).find(g => g.id === groupId);
    if (!group) return;

    const nameEl = document.getElementById('group-detail-name');
    const summaryEl = document.getElementById('group-detail-members-summary');
    const breadEl = document.getElementById('header-breadcrumb');
    if (nameEl) nameEl.textContent = group.name;
    if (summaryEl) {
      const count = group.member_count || (group.member_names ? group.member_names.length : 0);
      summaryEl.textContent = `${count} member${count !== 1 ? 's' : ''} · Currency: ${group.currency || 'INR'}`;
    }
    if (breadEl) breadEl.textContent = 'Groups / ' + group.name;

    // Expenses tab
    const tbody = document.querySelector('#group-detail-expenses tbody');
    if (tbody) {
      tbody.innerHTML = '';
      const groupExpenses = (state.expenses || []).filter(e => e.group_id === groupId);
      if (groupExpenses.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="px-space-md py-6 text-center text-secondary">No expenses in this group yet.</td></tr>`;
      } else {
        groupExpenses.forEach(e => {
          const tr = document.createElement('tr');
          tr.className = 'h-[44px] hover:bg-surface-container-low transition-colors';
          tr.innerHTML = `
            <td class="px-space-md font-tabular-body text-tabular-body text-secondary whitespace-nowrap">${formatDate(e.date)}</td>
            <td class="px-space-md font-medium text-on-surface">${escHtml(e.description)}</td>
            <td class="px-space-md text-on-surface-variant">${escHtml(e.payer_name || '')}</td>
            <td class="px-space-md font-tabular-body text-right">${state.currency}${(e.amount || 0).toFixed(2)}</td>
            <td class="px-space-md text-center">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-caption font-medium ${e.status === 'Settled' ? 'bg-surface-container-high text-on-surface-variant' : 'bg-tertiary-fixed text-on-tertiary-fixed'}">
                ${escHtml(e.status || 'Pending')}
              </span>
            </td>
          `;
          tbody.appendChild(tr);
        });
      }
    }

    // Members tab
    const membersContainer = document.getElementById('group-detail-members');
    if (membersContainer) {
      membersContainer.innerHTML = '';
      (group.member_names || []).forEach(name => {
        const div = document.createElement('div');
        div.className = 'flex items-center gap-3 p-space-sm rounded-lg hover:bg-surface-container-low';
        const initials = name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
        div.innerHTML = `
          <div class="w-8 h-8 rounded-full bg-secondary-container text-on-secondary-container flex items-center justify-center text-xs font-semibold">${escHtml(initials)}</div>
          <span class="font-body-default text-body-default text-on-surface">${escHtml(name)}</span>
        `;
        membersContainer.appendChild(div);
      });
    }
  };

  // --- Router & View Management ---
  const views = ['dashboard', 'groups', 'group-detail', 'settle-up', 'reminders', 'reports', 'settings'];

  window.navigate = function (viewId) {
    if (!views.includes(viewId)) viewId = 'dashboard';

    document.querySelectorAll('.app-view').forEach(el => el.classList.add('hidden'));
    const target = document.getElementById('view-' + viewId);
    if (target) target.classList.remove('hidden');

    document.querySelectorAll('.nav-item').forEach(link => {
      const linkView = link.getAttribute('data-view');
      if (linkView === viewId || (viewId === 'group-detail' && linkView === 'groups')) {
        link.className = 'nav-item flex items-center gap-3 px-3 py-2 rounded-lg transition-colors bg-surface-container-high text-on-surface font-semibold';
        const icon = link.querySelector('.material-symbols-outlined');
        if (icon) icon.classList.add('text-primary');
      } else {
        link.className = 'nav-item flex items-center gap-3 px-3 py-2 rounded-lg text-on-surface-variant hover:bg-surface-container-high hover:text-on-surface font-label-md text-label-md transition-colors';
        const icon = link.querySelector('.material-symbols-outlined');
        if (icon) icon.classList.remove('text-primary');
      }
    });

    const breadcrumb = document.getElementById('header-breadcrumb');
    if (breadcrumb) {
      const titles = {
        'dashboard': 'Ledger Overview',
        'groups': 'Shared Groups',
        'group-detail': 'Group Details',
        'settle-up': 'Ledger Clearing',
        'reminders': 'Payment Reminders',
        'reports': 'Fiscal Statement',
        'settings': 'Preferences & Settings'
      };
      breadcrumb.textContent = titles[viewId] || 'Ledger Calm';
    }

    window.location.hash = viewId;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  window.addEventListener('hashchange', () => {
    const h = window.location.hash.replace('#', '');
    if (views.includes(h)) window.navigate(h);
  });

  // --- Theme Management ---
  window.toggleTheme = function () {
    const isDark = document.documentElement.classList.toggle('dark');
    state.theme = isDark ? 'dark' : 'light';
    updateThemeIcon();
    // Save theme preference via settings API
    fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key: 'theme', value: state.theme })
    }).catch(() => {});
  };

  function updateThemeIcon() {
    const icon = document.getElementById('theme-toggle-icon');
    const isDark = document.documentElement.classList.contains('dark');
    if (icon) icon.textContent = isDark ? 'light_mode' : 'dark_mode';
  }

  // --- Currency Formatting ---
  window.formatCurrency = function (amount) {
    const sign = amount < 0 ? '-' : '';
    const abs = Math.abs(amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${sign}${state.currency}${abs}`;
  };

  // --- Toast Notification System ---
  window.showToast = function (message, type = 'success') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-lg bg-surface-container-lowest text-on-surface ring-1 ring-outline-variant/60 shadow-xl text-sm transition-all transform translate-y-2 opacity-0 duration-200';

    const iconName = type === 'success' ? 'check_circle' : (type === 'error' ? 'error' : 'info');
    const iconColor = type === 'success' ? 'text-tertiary' : (type === 'error' ? 'text-error' : 'text-primary');

    toast.innerHTML = `
      <span class="material-symbols-outlined ${iconColor} text-[20px]">${iconName}</span>
      <span class="font-medium">${escHtml(message)}</span>
      <button class="ml-2 text-secondary hover:text-on-surface" onclick="this.parentElement.remove()">
        <span class="material-symbols-outlined text-[16px]">close</span>
      </button>
    `;

    container.appendChild(toast);
    setTimeout(() => toast.classList.remove('translate-y-2', 'opacity-0'), 10);
    setTimeout(() => {
      toast.classList.add('opacity-0', 'translate-y-2');
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  };

  // --- Modal Management ---
  window.openAddExpenseModal = function (prefilledGroupId) {
    if (!state.groups || state.groups.length === 0) {
      showToast('Please create a group first to start recording expenses', 'info');
      window.openNewGroupModal();
      return;
    }
    const modal = document.getElementById('modal-add-expense');
    if (modal) {
      modal.classList.remove('hidden');
      if (prefilledGroupId) {
        const sel = document.getElementById('expense-group');
        if (sel) sel.value = prefilledGroupId;
      }
      calculateSplitShares();
    }
  };

  window.closeAddExpenseModal = function () {
    const modal = document.getElementById('modal-add-expense');
    if (modal) modal.classList.add('hidden');
  };

  window.openRecordModal = function (payerMemberId, payeeMemberId, amount) {
    const modal = document.getElementById('record-modal');
    if (modal) {
      modal.classList.remove('hidden');
      if (payerMemberId) {
        const payerSel = document.getElementById('modal-payer-select');
        if (payerSel) payerSel.value = payerMemberId;
      }
      if (payeeMemberId) {
        const payeeSel = document.getElementById('modal-payee-select');
        if (payeeSel) payeeSel.value = payeeMemberId;
      }
      if (amount) {
        const amtInput = document.getElementById('modal-amount-input');
        if (amtInput) amtInput.value = amount;
      }
    }
  };

  window.closeRecordModal = function () {
    const modal = document.getElementById('record-modal');
    if (modal) modal.classList.add('hidden');
  };

  window.openNewGroupModal = function () {
    const modal = document.getElementById('modal-new-group');
    if (modal) {
      modal.classList.remove('hidden');
      const form = document.getElementById('newGroupForm');
      if (form) form.reset();
      const nameInput = document.getElementById('new-group-name');
      if (nameInput) {
        setTimeout(() => nameInput.focus(), 60);
      }
    }
  };

  window.closeNewGroupModal = function () {
    const modal = document.getElementById('modal-new-group');
    if (modal) modal.classList.add('hidden');
  };

  // --- Add Member to Existing Group Modal ---
  let activeDetailGroupId = null;

  window.openAddMemberModal = function (groupId) {
    const gId = groupId || activeDetailGroupId;
    if (!gId) {
      showToast('Please select a group first', 'error');
      return;
    }
    const group = (state.groups || []).find(g => g.id === gId);
    const modal = document.getElementById('modal-add-member');
    const gidInput = document.getElementById('add-member-group-id');
    const gnameLabel = document.getElementById('add-member-group-name');
    const form = document.getElementById('addMemberForm');

    if (modal) {
      if (gidInput) gidInput.value = gId;
      if (gnameLabel) gnameLabel.textContent = group ? `To group: ${group.name}` : 'To group';
      if (form) form.reset();
      modal.classList.remove('hidden');
      const nameInp = document.getElementById('add-member-name');
      if (nameInp) setTimeout(() => nameInp.focus(), 60);
    }
  };

  window.closeAddMemberModal = function () {
    const modal = document.getElementById('modal-add-member');
    if (modal) modal.classList.add('hidden');
  };

  window.handleAddMember = async function (e) {
    e.preventDefault();
    const groupId = document.getElementById('add-member-group-id')?.value;
    const name = document.getElementById('add-member-name')?.value?.trim();
    const email = document.getElementById('add-member-email')?.value?.trim();

    if (!groupId || !name) {
      showToast('Member name is required', 'error');
      return;
    }

    try {
      const res = await fetch('/api/groups/member', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ groupId, name, email: email || null })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to add member');
      showToast(`Member "${name}" added to group!`, 'success');
      window.closeAddMemberModal();
      await fetchState();
      if (groupId) window.loadGroupDetail(groupId);
    } catch (err) {
      showToast(err.message, 'error');
    }
  };

  window.toggleQuickSearch = function () {
    const searchInput = document.getElementById('ledger-search-input');
    if (searchInput) {
      window.navigate('dashboard');
      searchInput.focus();
      showToast('Quick filter activated in Dashboard', 'info');
    }
  };

  // --- Split Engine ---
  let currentSplitMode = 'equal';

  function calculateSplitShares() {
    const totalAmountInput = document.getElementById('expense-amount');
    const total = parseFloat(totalAmountInput?.value || 0);
    const memberChecks = document.querySelectorAll('.member-check');
    const checked = Array.from(memberChecks).filter(c => c.checked);
    const count = checked.length || 1;
    const share = count > 0 ? (total / count) : 0;

    document.querySelectorAll('.member-input').forEach(input => {
      const check = document.querySelector(`.member-check[data-id="${input.dataset.member}"]`);
      if (check && check.checked) {
        if (currentSplitMode === 'equal') input.value = share.toFixed(2);
      } else {
        input.value = '0.00';
      }
    });

    updateVerificationBar();
  }

  function updateVerificationBar() {
    const totalAmountInput = document.getElementById('expense-amount');
    const total = parseFloat(totalAmountInput?.value || 0);
    let sum = 0;
    document.querySelectorAll('.member-input').forEach(input => { sum += parseFloat(input.value || 0); });

    const diff = Math.abs(sum - total);
    const isBalanced = diff < 0.05 && total > 0;

    const statusEl = document.getElementById('verificationStatus');
    const iconEl = document.getElementById('balanceIcon');
    const barEl = document.getElementById('splitProgressBar');
    const mismatchBanner = document.getElementById('mismatchBanner');
    const diffEl = document.getElementById('discrepancyDiff');
    const numbersEl = document.getElementById('verificationNumbers');

    if (numbersEl) {
      numbersEl.innerHTML = `Total split: <span class="font-medium">${state.currency}${sum.toFixed(2)}</span> of ${state.currency}${total.toFixed(2)}`;
    }

    if (isBalanced) {
      if (statusEl) statusEl.textContent = 'Balanced distribution';
      if (iconEl) { iconEl.textContent = 'check_circle'; iconEl.className = 'material-symbols-outlined text-[16px] text-tertiary'; }
      if (barEl) { barEl.style.width = '100%'; barEl.className = 'bg-tertiary h-full w-full transition-all duration-300'; }
      if (mismatchBanner) mismatchBanner.classList.add('hidden');
    } else {
      if (statusEl) statusEl.textContent = 'Discrepancy detected';
      if (iconEl) { iconEl.textContent = 'warning'; iconEl.className = 'material-symbols-outlined text-[16px] text-error'; }
      if (barEl) {
        const pct = total > 0 ? Math.min(100, (sum / total) * 100) : 0;
        barEl.style.width = pct + '%';
        barEl.className = 'bg-error h-full transition-all duration-300';
      }
      if (mismatchBanner) {
        mismatchBanner.classList.remove('hidden');
        if (diffEl) diffEl.textContent = `${sum < total ? '-' : '+'}${state.currency}${diff.toFixed(2)}`;
      }
    }
  }

  // --- Group Detail Tab Switching ---
  window.switchGroupTab = function (tab) {
    const expensesBtn = document.getElementById('tab-expenses-btn');
    const balancesBtn = document.getElementById('tab-balances-btn');
    const membersBtn = document.getElementById('tab-members-btn');
    const base = 'px-3.5 py-1.5 rounded-md font-label-md text-label-md text-on-surface-variant hover:text-on-surface transition-all flex items-center gap-2';
    const active = 'px-3.5 py-1.5 rounded-md font-label-md text-label-md bg-surface-container-lowest text-on-surface font-semibold shadow-sm transition-all flex items-center gap-2';
    if (expensesBtn) expensesBtn.className = tab === 'expenses' ? active : base;
    if (balancesBtn) balancesBtn.className = tab === 'balances' ? active : base;
    if (membersBtn) membersBtn.className = tab === 'members' ? active : base;
  };

  // --- HTML Escape Helpers ---
  function escHtml(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function escAttr(s) {
    return String(s || '').replace(/'/g, "\\'");
  }

  // --- Export CSV ---
  function exportCSV() {
    let csv = 'Date,Description,Group,Paid By,Amount,Status,Category\n';
    (state.expenses || []).forEach(e => {
      csv += `"${formatDate(e.date)}","${e.description || ''}","${e.group_name || ''}","${e.payer_name || ''}","${(e.amount || 0).toFixed(2)}","${e.status || ''}","${e.category || ''}"\n`;
    });
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `expense_splitter_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Ledger CSV statement downloaded!', 'success');
  }

  // --- DOM Ready ---
  document.addEventListener('DOMContentLoaded', () => {

    // Initial view from hash
    const initialView = window.location.hash.replace('#', '') || 'dashboard';
    window.navigate(initialView);

    // Apply saved theme from initial state
    const savedTheme = document.documentElement.classList.contains('dark') ? 'dark' : 'light';
    updateThemeIcon();

    // Fetch initial state
    fetchState();

    // ---- Auth Form Wiring ----
    const registerForm = document.getElementById('register-form');
    if (registerForm) {
      registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('register-name')?.value?.trim();
        const email = document.getElementById('register-email')?.value?.trim();
        const password = document.getElementById('register-password')?.value;
        if (!name || !email || !password) {
          showAuthError('Please fill in all fields');
          showToast('Please fill in all fields', 'error');
          return;
        }
        try {
          const res = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
          });
          const data = await res.json();
          if (!res.ok) throw new Error(data.error || 'Registration failed');
          showToast('Account created! Welcome, ' + name + '!', 'success');
          registerForm.reset();
          window.hideAuthOverlay();
          await fetchState();
        } catch (err) {
          showAuthError(err.message);
          showToast(err.message, 'error');
        }
      });
    }

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
      loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email')?.value?.trim();
        const password = document.getElementById('login-password')?.value;
        if (!email || !password) {
          showAuthError('Please enter email and password');
          showToast('Please enter email and password', 'error');
          return;
        }
        try {
          const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
          });
          const data = await res.json();
          if (!res.ok) throw new Error(data.error || 'Login failed');
          showToast('Welcome back, ' + data.user.name + '!', 'success');
          loginForm.reset();
          window.hideAuthOverlay();
          await fetchState();
        } catch (err) {
          showAuthError(err.message);
          showToast(err.message, 'error');
        }
      });
    }

    // Tab buttons in auth overlay
    const showLoginBtn = document.getElementById('auth-show-login');
    const showRegisterBtn = document.getElementById('auth-show-register');
    if (showLoginBtn) {
      showLoginBtn.addEventListener('click', (e) => {
        e.preventDefault();
        window.switchAuthTab('login');
      });
    }
    if (showRegisterBtn) {
      showRegisterBtn.addEventListener('click', (e) => {
        e.preventDefault();
        window.switchAuthTab('register');
      });
    }

    // Sign out
    const signOutBtn = document.getElementById('btn-sign-out');
    if (signOutBtn) {
      signOutBtn.addEventListener('click', async () => {
        await fetch('/api/auth/logout', { method: 'POST' });
        state.user = null;
        showToast('Signed out successfully', 'success');
        window.showAuthOverlay('login');
      });
    }

    // ---- Add Expense Modal ----
    const addExpenseModal = document.getElementById('modal-add-expense');
    if (addExpenseModal) {
      const closeBtn = addExpenseModal.querySelector('button[aria-label="Close dialog"]');
      if (closeBtn) closeBtn.onclick = window.closeAddExpenseModal;
      addExpenseModal.addEventListener('click', (e) => {
        if (e.target === addExpenseModal) window.closeAddExpenseModal();
      });

      // Split mode
      const splitBtns = addExpenseModal.querySelectorAll('#splitModeSelector .split-btn');
      splitBtns.forEach(btn => {
        btn.addEventListener('click', () => {
          splitBtns.forEach(b => {
            b.className = 'split-btn h-full rounded-[6px] font-label-md text-label-md flex items-center justify-center text-secondary hover:text-on-surface transition-all';
          });
          btn.className = 'split-btn h-full rounded-[6px] font-label-md text-label-md flex items-center justify-center transition-all bg-primary-container text-on-primary';
          currentSplitMode = btn.getAttribute('data-split');
          calculateSplitShares();
        });
      });

      const amountInput = document.getElementById('expense-amount');
      if (amountInput) amountInput.addEventListener('input', calculateSplitShares);

      // Form submit — POST to API
      const form = document.getElementById('expenseForm');
      if (form) {
        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          const description = document.getElementById('expense-desc')?.value?.trim() || 'Shared Expense';
          const total = parseFloat(document.getElementById('expense-amount')?.value || 0);
          const groupId = document.getElementById('expense-group')?.value;
          const payerMemberId = document.getElementById('expense-payer')?.value;
          const category = document.getElementById('expense-category')?.value || 'General';
          const dateVal = document.getElementById('expense-date')?.value || new Date().toISOString().slice(0, 10);

          if (total <= 0) { showToast('Please enter a valid amount', 'error'); return; }
          if (!groupId) { showToast('Please select a group', 'error'); return; }

          // Collect splits
          const splits = [];
          document.querySelectorAll('.member-check').forEach(chk => {
            if (chk.checked) {
              const memberId = chk.getAttribute('data-id');
              const amtEl = document.querySelector(`.member-input[data-member="${memberId}"]`);
              const amt = parseFloat(amtEl?.value || 0);
              splits.push({ member_id: memberId, amount: amt });
            }
          });

          try {
            const res = await fetch('/api/expenses', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                group_id: groupId,
                description,
                amount: total,
                payer_member_id: payerMemberId,
                date: dateVal,
                category,
                splits
              })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Failed to add expense');
            showToast(`Expense "${description}" added!`, 'success');
            window.closeAddExpenseModal();
            form.reset();
            await fetchState();
          } catch (err) {
            showToast(err.message, 'error');
          }
        });
      }
    }

    // ---- Record Settlement Modal ----
    const recordModal = document.getElementById('record-modal');
    if (recordModal) {
      const closeBtn = document.getElementById('close-modal-btn');
      if (closeBtn) closeBtn.onclick = window.closeRecordModal;
      recordModal.addEventListener('click', (e) => {
        if (e.target === recordModal) window.closeRecordModal();
      });

      const form = document.getElementById('record-settlement-form');
      if (form) {
        form.addEventListener('submit', async (e) => {
          e.preventDefault();
          const payerMemberId = document.getElementById('modal-payer-select')?.value;
          const payeeMemberId = document.getElementById('modal-payee-select')?.value;
          const amount = parseFloat(document.getElementById('modal-amount-input')?.value || 0);
          const method = document.getElementById('modal-method-select')?.value || 'Cash';
          if (amount <= 0) { showToast('Please specify a positive settlement amount', 'error'); return; }

          try {
            const res = await fetch('/api/payments', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                payer_member_id: payerMemberId,
                payee_member_id: payeeMemberId,
                amount,
                method
              })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || 'Failed to record settlement');
            showToast(`Settlement of ${state.currency}${amount.toFixed(2)} recorded!`, 'success');
            window.closeRecordModal();
            await fetchState();
          } catch (err) {
            showToast(err.message, 'error');
          }
        });
      }
    }

    // ---- New Group Modal & Add Member Modal Backdrop Click ----
    const newGroupModal = document.getElementById('modal-new-group');
    if (newGroupModal) {
      newGroupModal.addEventListener('click', (e) => {
        if (e.target === newGroupModal) window.closeNewGroupModal();
      });
    }

    const addMemberModal = document.getElementById('modal-add-member');
    if (addMemberModal) {
      addMemberModal.addEventListener('click', (e) => {
        if (e.target === addMemberModal) window.closeAddMemberModal();
      });
    }

    // ---- Button wiring (fallback) ----
    document.querySelectorAll('button').forEach(btn => {
      const txt = (btn.innerText || btn.textContent || '').trim();
      if (txt.includes('Add Expense') || txt.includes('Add expense')) {
        if (!btn.hasAttribute('data-wired') && !btn.hasAttribute('onclick')) {
          btn.setAttribute('data-wired', '1');
          btn.addEventListener('click', (e) => { e.preventDefault(); window.openAddExpenseModal(); });
        }
      }
      if (txt.includes('Record payment') || txt.includes('Record Payment')) {
        if (!btn.hasAttribute('data-wired') && !btn.hasAttribute('onclick')) {
          btn.setAttribute('data-wired', '1');
          btn.addEventListener('click', (e) => { e.preventDefault(); window.openRecordModal(); });
        }
      }
      if (txt.includes('New group') || txt.includes('New Group')) {
        if (!btn.hasAttribute('data-wired') && !btn.hasAttribute('onclick')) {
          btn.setAttribute('data-wired', '1');
          btn.addEventListener('click', (e) => { e.preventDefault(); window.openNewGroupModal(); });
        }
      }
    });

    // Nudge (send all) button
    const sendAllBtn = document.getElementById('btn-send-all');
    if (sendAllBtn) {
      sendAllBtn.addEventListener('click', () => {
        showToast('All pending payment reminders dispatched!', 'success');
      });
    }

    // Export buttons
    const csvBtn = document.getElementById('btn-export-csv');
    if (csvBtn) csvBtn.addEventListener('click', exportCSV);
    const pdfBtn = document.getElementById('btn-export-pdf');
    if (pdfBtn) pdfBtn.addEventListener('click', () => window.print());

    // Settings save
    const saveSettingsBtn = document.getElementById('save-btn');
    if (saveSettingsBtn) {
      saveSettingsBtn.addEventListener('click', async () => {
        const currencyEl = document.getElementById('settings-currency') || document.getElementById('currency-select');
        const themeRadio = document.querySelector('input[name="theme_mode"]:checked');
        const currency = currencyEl?.value || state.currency;
        const theme = themeRadio ? themeRadio.value : state.theme;
        try {
          await fetch('/api/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key: 'currency', value: currency })
          });
          await fetch('/api/settings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key: 'theme', value: theme })
          });
          const status = document.getElementById('save-status');
          if (status) { status.classList.remove('opacity-0'); setTimeout(() => status.classList.add('opacity-0'), 3000); }
          showToast('Settings saved!', 'success');
          await fetchState();
        } catch (err) {
          showToast('Failed to save settings', 'error');
        }
      });
    }

    // Search filter for ledger table
    const searchInput = document.getElementById('ledger-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', () => {
        const q = searchInput.value.toLowerCase();
        document.querySelectorAll('#ledger-table tbody tr').forEach(tr => {
          tr.style.display = tr.textContent.toLowerCase().includes(q) ? '' : 'none';
        });
      });
    }

    // Group search filter
    const groupSearchInput = document.getElementById('group-search-input');
    if (groupSearchInput) {
      groupSearchInput.addEventListener('input', () => {
        const q = groupSearchInput.value.toLowerCase();
        document.querySelectorAll('#groups-table tbody tr').forEach(tr => {
          tr.style.display = tr.textContent.toLowerCase().includes(q) ? '' : 'none';
        });
      });
    }
  });

  // New Group form submit — POST to API
  window.handleNewGroup = async function (e) {
    e.preventDefault();
    const name = document.getElementById('new-group-name')?.value?.trim();
    const currency = document.getElementById('new-group-currency')?.value || 'INR';
    const cat = document.getElementById('new-group-category')?.value || 'General';
    const members = document.getElementById('new-group-members')?.value?.trim() || '';

    if (!name) {
      showToast('Please enter a group name', 'error');
      return;
    }

    const submitBtn = document.getElementById('btn-submit-new-group');
    if (submitBtn) submitBtn.disabled = true;

    try {
      const res = await fetch('/api/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, currency, category: cat, members })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to create group');
      showToast(`Group "${name}" created successfully!`, 'success');
      window.closeNewGroupModal();
      const form = document.getElementById('newGroupForm');
      if (form) form.reset();
      await fetchState();
      window.navigate('groups');
      if (data.id) {
        window.loadGroupDetail(data.id);
      }
    } catch (err) {
      showToast(err.message, 'error');
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  };

})();
