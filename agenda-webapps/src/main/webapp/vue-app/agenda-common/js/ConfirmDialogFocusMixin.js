/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */

/**
 * The focus of the agenda's recurrent-event confirmation dialogs, as the
 * platform's exo-confirm-dialog handles it: when the dialog opens, the focus
 * goes to its Cancel button; when it closes, back to the control that opened
 * it, while that control is still shown and the focus was not moved elsewhere.
 *
 * <p>The component keeps its own `dialog` boolean, gives its Cancel button
 * `ref="cancelButton"` and its card `ref="card"`, and labels the card with
 * `confirmDialogTitleId`.
 */
export default {
  data: () => ({
    confirmDialogOpener: null,
  }),
  computed: {
    confirmDialogTitleId() {
      return `agendaConfirmDialogTitle${this._uid}`;
    },
  },
  watch: {
    dialog(opened) {
      if (opened) {
        this.confirmDialogOpener = this.confirmDialogActivationOrigin();
        this.focusConfirmDialogCancelButton();
      } else {
        this.restoreConfirmDialogOpenerFocus();
      }
    },
  },
  methods: {
    confirmDialogActivationOrigin() {
      // the control that opened it, which a click does not always leave focused, and the button of the menu
      // it was chosen in: social's ActivationOrigin.js, exposed by the common bundle
      const activationOrigin = this.$activationOrigin;
      const element = activationOrigin?.element?.() || document.activeElement;
      return activationOrigin?.menuOrigin?.(element) || element;
    },
    focusConfirmDialogCancelButton() {
      // v-dialog focuses its own container first
      window.setTimeout(() => {
        if (this.dialog && !this.$refs.card?.$el?.contains(document.activeElement)) {
          this.$refs.cancelButton?.focus?.();
        }
      }, 100);
    },
    isConfirmDialogFocusLost(active) {
      return !active || active === document.body || !!this.$refs.card?.$el?.contains(active);
    },
    restoreConfirmDialogOpenerFocus() {
      const opener = this.confirmDialogOpener;
      this.confirmDialogOpener = null;
      window.setTimeout(() => {
        if (opener?.isConnected
            && opener !== document.body
            && opener.getClientRects().length
            && !opener.disabled
            && this.isConfirmDialogFocusLost(document.activeElement)) {
          opener.focus?.();
        }
      }, 50);
    },
  },
};
