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
 * The focus of the agenda's recurrent-event confirmation dialogs: the contract
 * every confirmation dialog of the platform shares, social's DialogFocus.js,
 * which the common bundle exposes as $dialogFocus. When the dialog opens, the
 * focus goes to its Cancel button, and the keystroke that opened it does not
 * press that button; when it closes, the focus goes back to the control that
 * opened it, or, when the confirmed action removed it, to what replaced it.
 *
 * <p>The component keeps its own `dialog` boolean, gives its Cancel button
 * `ref="cancelButton"` and its card `ref="card"`, labels the card with
 * `confirmDialogTitleId` and describes it with `confirmDialogQuestionId`,
 * calls `captureConfirmDialogOpener` when it is opened and
 * `confirmDialogConfirmed` when its action is confirmed.
 */
export default {
  computed: {
    confirmDialogTitleId() {
      return `agendaConfirmDialogTitle${this._uid}`;
    },
    confirmDialogQuestionId() {
      return `agendaConfirmDialogQuestion${this._uid}`;
    },
  },
  watch: {
    dialog(opened) {
      if (opened) {
        this.confirmDialogActionConfirmed = false;
        this.confirmDialogFocus?.open();
      } else {
        this.confirmDialogFocus?.close({confirmed: this.confirmDialogActionConfirmed});
      }
    },
  },
  created() {
    this.confirmDialogActionConfirmed = false;
    this.confirmDialogFocus = this.$dialogFocus?.create({
      content: () => this.$refs.card?.$el,
      defaultButton: () => this.$refs.cancelButton,
    });
  },
  beforeDestroy() {
    this.confirmDialogFocus?.destroy();
  },
  methods: {
    captureConfirmDialogOpener() {
      // read during the click: a menu that closes on this click resets its button before the dialog is shown
      this.confirmDialogFocus?.capture();
    },
    confirmDialogConfirmed() {
      // the confirmed action may remove or disable the opener: the focus follows it once the dialog closes
      this.confirmDialogActionConfirmed = true;
    },
  },
};
