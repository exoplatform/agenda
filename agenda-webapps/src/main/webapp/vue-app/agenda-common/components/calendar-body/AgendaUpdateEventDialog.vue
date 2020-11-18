<template>
  <exo-confirm-dialog
    ref="NoAllowedToUpdateEventDialog"
    :message="$t('agenda.update.notAllowed')"
    :title="$t('agenda.actionProhibited')"
    :ok-label="$t('agenda.button.ok')"
    @ok="closeDialog"
    @closed="closeDialog" />
</template>

<script>
export default {
  data () {
    return {
      event: null,
      saving: false,
    };
  },
  created() {
    this.$root.$on('agenda-event-cancel-drag-drop', this.open);
  },
  methods: {
    open () {
      this.$refs.NoAllowedToUpdateEventDialog.open();
    },
    closeDialog() {
      this.$refs.NoAllowedToUpdateEventDialog.close();
      this.$root.$emit('agenda-event-save-cancel');
    }
  }
};
</script>
