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

const OPENER_FOLLOW_DELAY = 10000;
const MAIN_CONTENT_ID = 'UISiteBody';

/**
 * The focus of the agenda's recurrent-event confirmation dialogs, as the
 * platform's exo-confirm-dialog (social ConfirmDialog.vue) handles it: when the
 * dialog opens, the focus goes to its Cancel button, and the keystroke that
 * opened it does not press that button; when it closes, the focus goes back to
 * the control that opened it. A confirmed action may close the event form that
 * holds that control, or disable it while its request runs: the focus then goes
 * to the start of the page content, or back to the control once it is enabled
 * again, until the user acts on the page or ten seconds have passed.
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
        window.addEventListener('focusin', this.redirectConfirmDialogContainerFocus, true);
        this.$openingKeystrokeGuard?.ignore?.(this.guardConfirmDialogOpeningKeystroke);
        this.focusConfirmDialogCancelButton();
      } else {
        this.stopConfirmDialogFocusListeners();
        this.restoreConfirmDialogOpenerFocus();
      }
    },
  },
  beforeDestroy() {
    this.stopConfirmDialogFocusListeners();
  },
  methods: {
    confirmDialogActivationOrigin() {
      // the control that opened it, which a click does not always leave focused, and the button of the menu
      // it was chosen in: social's ActivationOrigin.js, exposed by the common bundle
      const activationOrigin = this.$activationOrigin;
      const element = activationOrigin?.element?.() || document.activeElement;
      return activationOrigin?.menuOrigin?.(element) || element;
    },
    guardConfirmDialogOpeningKeystroke(event) {
      this.$openingKeystrokeGuard.handle(event, this.guardConfirmDialogOpeningKeystroke);
    },
    stopConfirmDialogFocusListeners() {
      window.removeEventListener('focusin', this.redirectConfirmDialogContainerFocus, true);
      this.$openingKeystrokeGuard?.stop?.(this.guardConfirmDialogOpeningKeystroke);
    },
    redirectConfirmDialogContainerFocus(event) {
      // v-dialog focuses its container first: a screen reader would read the dialog twice before its button
      const card = this.$refs.card?.$el;
      const button = this.$refs.cancelButton;
      if (card && button && event.target !== card && event.target?.contains?.(card)) {
        window.removeEventListener('focusin', this.redirectConfirmDialogContainerFocus, true);
        button.focus();
      }
    },
    focusConfirmDialogCancelButton() {
      // fallback when v-dialog does not focus its own container
      window.setTimeout(() => {
        if (this.dialog && !this.$refs.card?.$el?.contains(document.activeElement)) {
          this.$refs.cancelButton?.focus?.();
        }
      }, 100);
    },
    isConfirmDialogFocusLost(active) {
      // on the body, on this dialog, on a dialog closing with it (the capacity warning the save dialog opens),
      // or on the start of the page content another dialog fell back to
      return !active
        || active === document.body
        || active.id === MAIN_CONTENT_ID
        || !!this.$refs.card?.$el?.contains(active)
        || !!active.closest?.('.v-dialog__content:not(.v-dialog__content--active)');
    },
    isConfirmDialogOpenerShown(opener) {
      return opener?.isConnected
        && opener !== document.body
        && opener.getClientRects().length
        && window.getComputedStyle(opener).visibility !== 'hidden'
        && !opener.closest('[aria-hidden="true"]');
    },
    focusConfirmDialogMainContent() {
      // the skip link's target, focusable on every portal page, made so here as well when a page lacks it
      const main = document.getElementById(MAIN_CONTENT_ID);
      if (main && !main.hasAttribute('tabindex')) {
        main.setAttribute('tabindex', '-1');
      }
      main?.focus?.();
    },
    placeConfirmDialogFocus(opener) {
      if (!this.isConfirmDialogOpenerShown(opener)) {
        this.focusConfirmDialogMainContent();
        return true;
      } else if (!opener.disabled) {
        opener.focus?.();
        return true;
      }
      // a disabled opener (its action is running) takes the focus back once enabled
      return false;
    },
    restoreConfirmDialogOpenerFocus() {
      const opener = this.confirmDialogOpener;
      this.confirmDialogOpener = null;
      window.setTimeout(() => {
        if (!this.isConfirmDialogFocusLost(document.activeElement)) {
          return;
        }
        this.placeConfirmDialogFocus(opener);
        // the confirmed action may close the form that holds the opener, or disable it, once its request ends
        this.followConfirmDialogOpener(opener);
      }, 50);
    },
    followConfirmDialogOpener(opener) {
      if (!opener || opener === document.body) {
        return;
      }
      let timeout = null;
      const observer = new MutationObserver(() => {
        const active = document.activeElement;
        // only while the focus is still on the opener, or lost: never take it from where the user moved it
        if (active !== opener && !this.isConfirmDialogFocusLost(active)) {
          stop();
        } else if (!this.isConfirmDialogOpenerShown(opener)) {
          if (active?.id !== MAIN_CONTENT_ID) {
            this.focusConfirmDialogMainContent();
          }
          stop();
        } else if (active !== opener && !opener.disabled) {
          opener.focus?.();
        }
      });
      // the user acting on the page ends the following
      function stop() {
        observer.disconnect();
        window.clearTimeout(timeout);
        document.removeEventListener('pointerdown', stop, true);
        document.removeEventListener('keydown', stop, true);
      }
      document.addEventListener('pointerdown', stop, true);
      document.addEventListener('keydown', stop, true);
      timeout = window.setTimeout(stop, OPENER_FOLLOW_DELAY);
      observer.observe(document.body, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['disabled', 'style', 'class'],
      });
    },
  },
};
