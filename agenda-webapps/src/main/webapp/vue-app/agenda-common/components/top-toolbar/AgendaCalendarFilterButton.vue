<template>
  <v-btn
    :title="$t('agenda.filterAgendaTooltip')"
    icon
    max-width="36"
    max-height="36"
    @click="$root.$emit('agenda-calendar-owners-drawer-open',currentSpace)">
    <v-icon dark size="20">
      fa-sliders-h
    </v-icon>
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
