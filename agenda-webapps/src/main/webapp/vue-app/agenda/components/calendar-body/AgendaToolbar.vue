<template>
  <v-toolbar flat color="white">
    <v-btn
      outlined
      class="btn mr-4"
      color="grey darken-2"
      @click="setToday">
      {{ $t('agenda.toDay') }}
    </v-btn>
    <v-row align="center" justify="center">
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