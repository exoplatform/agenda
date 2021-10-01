<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-flex class="d-flex agenda-empty-timeline">
    <div class="ma-auto py-5 d-flex flex-column align-center">
      <i class="uiIconCalendarEmpty"></i>
      <span class="title mt-5">
        {{ $t('agenda.noEvent') }}
      </span>
      <span class="sub-title-1 text-sub-title">
        {{ $t('agenda.noEventYet') }}
      </span>
    </div>
  </v-flex>
</template>