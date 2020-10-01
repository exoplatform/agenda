<template>
  <v-flex class="agenda-mobile-header d-flex flex-row pa-2 border-radius border-color">
    <date-picker
      v-model="period.start"
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
};
</script>