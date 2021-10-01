<!--
SPDX-FileCopyrightText: 2021 eXo Platform SAS

SPDX-License-Identifier: AGPL-3.0-only
-->

<template>
  <v-tabs
    v-model="calendarType"
    align-with-title
    centered
    class="text-capitalize">
    <v-tab href="#day" dark>
      {{ $t('agenda.label.viewDay') }}
    </v-tab>
    <v-tab href="#week" dark>
      {{ $t('agenda.label.viewWeek') }}
    </v-tab>
    <v-tab href="#month" dark>
      {{ $t('agenda.label.viewMonth') }}
    </v-tab>
  </v-tabs>
</template>
<script>
export default {
  props: {
    calendarType: {
      type: String,
      default: null
    },
  },
  watch: {
    calendarType(newValue, oldValue) {
      if (oldValue) {
        this.$root.$emit('agenda-change-period-type', this.calendarType);
      }
    },
  },
};
</script>
