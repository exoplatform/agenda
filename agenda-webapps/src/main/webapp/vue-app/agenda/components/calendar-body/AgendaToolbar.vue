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
        fab
        text
        small
        color="grey darken-2"
        @click="prevDate">
        <v-icon small>
          mdi-menu-left
        </v-icon>
      </v-btn>
      <v-toolbar-title v-if="periodTitle">
        {{ periodTitle }}
      </v-toolbar-title>
      <v-btn
        fab
        text
        small
        color="grey darken-2"
        @click="nextDate">
        <v-icon small>
          mdi-menu-right
        </v-icon>
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