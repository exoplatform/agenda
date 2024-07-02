<template>
  <v-flex class="d-flex agenda-empty-timeline">
    <div class="ma-auto py-5 d-flex flex-column align-center">
      <i class="uiIconCalendarEmpty"></i>
      <span class="text-title mt-5">
        {{ $t('agenda.noEvent') }}
      </span>
      <span class="text-subtitle">
        {{ $t('agenda.noEventYet') }}
      </span>
    </div>
  </v-flex>
</template>