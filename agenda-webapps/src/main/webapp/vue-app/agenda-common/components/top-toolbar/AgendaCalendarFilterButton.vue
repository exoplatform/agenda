<template>
  <v-btn
    :title="$t('agenda.filterAgendaTooltip')"
    icon
    max-width="36"
    max-height="36"
    @click="$root.$emit('agenda-filter-drawer-open',currentSpace,settings)">
    <v-icon size="20" class="text-light-color">
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
    settings: {
      type: Object,
      default: () => null
    },
  },
};
</script>
