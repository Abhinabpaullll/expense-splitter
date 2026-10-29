package com.expensesplitter;

import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import static org.assertj.core.api.Assertions.assertThat;

class IkonliTest {
    @Test
    void testIconConstants() {
        FontIcon i1 = new FontIcon(MaterialDesignV.VIEW_DASHBOARD_OUTLINE);
        FontIcon i2 = new FontIcon(MaterialDesignA.ACCOUNT_GROUP_OUTLINE);
        FontIcon i3 = new FontIcon(MaterialDesignB.BELL_OUTLINE);
        FontIcon i4 = new FontIcon(MaterialDesignF.FILE_DOCUMENT_OUTLINE);
        FontIcon i5 = new FontIcon(MaterialDesignC.COG_OUTLINE);
        FontIcon i6 = new FontIcon(MaterialDesignP.PLUS);
        FontIcon i7 = new FontIcon(MaterialDesignA.ARROW_LEFT);
        FontIcon i8 = new FontIcon(MaterialDesignM.MAGNIFY);

        assertThat(i1.getIconCode()).isNotNull();
        assertThat(i2.getIconCode()).isNotNull();
        assertThat(i3.getIconCode()).isNotNull();
        assertThat(i4.getIconCode()).isNotNull();
        assertThat(i5.getIconCode()).isNotNull();
        assertThat(i6.getIconCode()).isNotNull();
        assertThat(i7.getIconCode()).isNotNull();
        assertThat(i8.getIconCode()).isNotNull();
    }
}

