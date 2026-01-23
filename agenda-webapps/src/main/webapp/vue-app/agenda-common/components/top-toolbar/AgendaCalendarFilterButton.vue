<template>
  <v-btn
    :title="$t('agenda.filterAgendaTooltip')"
    icon
    max-width="36"
    max-height="36"
    @click="$root.$emit('agenda-filter-drawer-open')">
    <v-icon size="20" class="text-light-color">
      fa-sliders-h
    </v-icon>
  </v-btn>
</template>