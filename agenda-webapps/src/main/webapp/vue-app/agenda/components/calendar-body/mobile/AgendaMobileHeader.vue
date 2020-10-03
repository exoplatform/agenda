<template>
  <v-flex class="agenda-mobile-header d-flex flex-row pa-2 border-radius border-color">
    <agenda-create-event-button class="my-auto" />
    <date-picker
      v-model="periodStart"
      class="d-flex flex-grow-1 ma-auto agenda-header-date-picker" />
    <agenda-calendar-filter-button
      :current-space="currentSpace"
      :owner-ids="ownerIds" />
  </v-flex>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    ownerIds: {
      type: Array,
      default: null
    },
    period: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    periodStart: null,
  }),
  watch: {
    periodStart(newVal, oldVal) {
      if (!oldVal || !newVal) {
        return;
      }
      if (this.$agendaUtils.toRFC3339(oldVal, true) !== this.$agendaUtils.toRFC3339(newVal, true)) {
        this.period.start = this.periodStart;
        this.$root.$emit('refresh');
      }
    },
  },
  created() {
    this.periodStart = this.period && this.period.start || new Date();
  },
};
</script>