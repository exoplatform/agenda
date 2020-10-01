<template>
  <v-btn
    v-if="!currentSpace"
    :title="$t('agenda.filterAgendaTooltip')"
    icon
    class="primary--text"
    @click="$root.$emit('agenda-calendar-owners-drawer-open')">
    <template v-if="ownerIds && ownerIds.length">
      <i class="uiIcon uiIcon24x24 settingsIcon primary--text"></i>
      ({{ ownerIds.length }})
    </template>
    <template v-else>
      <i class="uiIcon uiIcon24x24 settingsIcon text-color"></i>
    </template>
  </v-btn>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    period: {
      type: Object,
      default: null
    },
    ownerIds: {
      type: Array,
      default: null
    },
  },
};
</script>
