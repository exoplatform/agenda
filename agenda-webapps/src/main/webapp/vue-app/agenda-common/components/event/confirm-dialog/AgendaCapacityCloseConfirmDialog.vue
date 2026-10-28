<template>
  <exo-confirm-dialog
    ref="dialog"
    :title="$t('agenda.capacity.closeConfirm.title')"
    :message="$t('agenda.capacity.closeConfirm.message')"
    :ok-label="$t('agenda.button.confirm')"
    :cancel-label="$t('agenda.button.cancel')"
    persistent
    @ok="settle(true)"
    @dialog-closed="closed" />
</template>

<script>
const CLOSE_NEEDS_CONFIRMATION = 'agenda.capacity.closeNeedsConfirmation';

/**
 * Asked before an open event with a limit is closed, from the padlock or from
 * a form: closing removes the limit and confirms everyone on the waiting list
 * (D15).
 */
export default {
  created() {
    this.pending = null;
    this.destroyed = false;
    document.addEventListener('keydown', this.keepEscape, true);
  },
  beforeDestroy() {
    this.destroyed = true;
    document.removeEventListener('keydown', this.keepEscape, true);
  },
  methods: {
    /**
     * Escape answers nothing while the warning waits for its answer: the
     * warning is persistent, and the key would otherwise close what it sits
     * above, the event's dialog or the participants drawer, while the write
     * waits on the answer. Stopped on the document before it goes on to the
     * page's elements and to the document's listeners of the bubble phase,
     * the event dialog's and the drawers overlay's among them, wherever the
     * focus is: on the warning's first opening it is still on the page.
     *
     * @param {KeyboardEvent} event the key pressed, in the capture phase
     * @returns {void}
     */
    keepEscape(event) {
      if (event.key === 'Escape' && this.pending) {
        event.stopPropagation();
      }
    },
    /**
     * @returns {Promise} resolved when the organiser confirms, rejected with
     *          null when they cancel
     */
    confirm() {
      return new Promise((resolve, reject) => {
        this.pending = {resolve, reject};
        if (this.destroyed) {
          // Asked once its page is gone, by a refusal that came back after
          // Escape closed the event's page: nobody can answer it any more
          this.settle(false);
          return;
        }
        this.$refs.dialog.open();
        // Above the participants drawer the padlock is in, and above the
        // scope question a save of a series asks it from. On the next tick:
        // the content is rendered by the flush the opening queues
        this.$nextTick(() => this.$agendaUtils.raiseAboveOverlays(this.$refs.dialog.$refs.dialog, 1));
      });
    },
    /**
     * Writes with the confirmation given, or asks for it when the server
     * refuses a closing its caller could not see coming (a date of the series
     * with people waiting) and writes again once confirmed.
     *
     * @param {Function} write the write, given whether the closing is confirmed
     * @param {Boolean} confirmed whether the organiser already confirmed it
     * @returns {Promise} the write's, rejected with null when the organiser
     *          cancels the closing
     */
    writeConfirmingClose(write, confirmed) {
      return write(!!confirmed).catch(error => {
        if (!confirmed && error && error.code === CLOSE_NEEDS_CONFIRMATION) {
          return this.confirm().then(() => write(true));
        }
        throw error;
      });
    },
    closed() {
      this.$agendaUtils.lowerToSkin(this.$refs.dialog.$refs.dialog);
      this.settle(false);
    },
    settle(confirmed) {
      const pending = this.pending;
      this.pending = null;
      if (pending && confirmed) {
        pending.resolve();
      } else if (pending) {
        pending.reject(null);
      }
    },
  },
};
</script>
