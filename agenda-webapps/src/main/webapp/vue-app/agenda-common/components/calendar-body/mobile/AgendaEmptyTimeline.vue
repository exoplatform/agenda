<template>
  <v-flex class="d-flex agenda-empty-timeline">
    <div class="ma-auto py-5 d-flex flex-column align-center">
      <i class="uiIconCalendarEmpty"></i>
      <span class="title">
        {{ $t('agenda.noEvent') }}
      </span>
      <span class="sub-title-1 text-sub-title">
        {{ $t('agenda.noEventYet') }}
      </span>
    </div>
  </v-flex>
</template>