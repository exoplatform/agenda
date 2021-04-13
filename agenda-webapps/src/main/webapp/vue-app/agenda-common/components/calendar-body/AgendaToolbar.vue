<template>
  <v-toolbar flat color="white">
    <v-row>
      <v-col cols="2" class="my-auto">
        <v-btn
          outlined
          class="btn me-4"
          color="grey darken-2"
          @click="setToday">
          {{ $t('agenda.toDay') }}
        </v-btn>
      </v-col>
      <v-col
        cols="8"
        align="center"
        class="d-flex flex-row align-center justify-center flex-nowrap">
        <v-btn
          icon
          color="grey darken-2"
          @click="prevDate">
          <i class="uiIconArrowLeft uiIconMedium darkGreyIcon"></i>
        </v-btn>
        <div class="period-title text-uppercase">
          {{ periodTitle }}
        </div>
        <v-btn
          icon
          color="grey darken-2"
          @click="nextDate">
          <i class="uiIconArrowRight uiIconMedium darkGreyIcon"></i>
        </v-btn>
      </v-col>
      <v-col cols="2" />
    </v-row>
  </v-toolbar>
</template>
<script>
export default {
  props: {
    periodTitle: {
      type: String,
      default: null
    },
  },
  methods: {
    setToday() {
      this.$root.$emit('agenda-display-calendar-atDate');
    },
    nextDate() {
      this.$root.$emit('agenda-display-calendar-next');
    },
    prevDate() {
      this.$root.$emit('agenda-display-calendar-previous');
    }
  },
};
</script>