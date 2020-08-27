<template>
  <v-toolbar flat color="white">
    <v-btn
      outlined
      class="mr-4"
      color="grey darken-2"
      @click="setToday"
    >
      {{ $t('agenda.toDay') }}
    </v-btn>
    <v-btn
      fab
      text
      small
      color="grey darken-2"
      @click="prevDate"
    >
      <v-icon small>
        mdi-chevron-left
      </v-icon>
    </v-btn>
    <v-btn
      fab
      text
      small
      color="grey darken-2"
      @click="nextDate"
    >
      <v-icon small>
        mdi-chevron-right
      </v-icon>
    </v-btn>
    <v-toolbar-title v-if="periodTitle">
      {{ periodTitle }}
    </v-toolbar-title>
    <v-spacer />
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
    setToday () {
      this.$root.$emit('agenda-display-calendar-atDate');
    },
    prevDate () {
      this.$root.$emit('agenda-display-calendar-next');
    },
    nextDate () {
      this.$root.$emit('agenda-display-calendar-previous');
    }
  },
};
</script>