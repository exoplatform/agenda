<template>
  <v-toolbar flat color="white">
    <v-btn
      outlined
      class="mr-4"
      color="grey darken-2"
      @click="setToday">
      {{ $t('agenda.toDay') }}
    </v-btn>
    <v-btn
      fab
      text
      small
      color="grey darken-2"
      @click="prevDate">
      <v-icon small>mdi-chevron-left</v-icon>
    </v-btn>
    <v-btn
      fab
      text
      small
      color="grey darken-2"
      @click="nextDate">
      <v-icon small>mdi-chevron-right</v-icon>
    </v-btn>
    <v-toolbar-title v-if="title">
      {{ title.name }}
    </v-toolbar-title>
    <v-spacer />
  </v-toolbar>
</template>
<script>
export default {
  props: {
    title: {
      type: Object,
      default: () => ''
    }
  },
  data: () => ({
    focus: '',
  }),
  methods: {
    setToday () {
      this.focus = '';
      this.$root.$emit('set-to-day',this.focus);
    },
    prevDate () {
      this.$refs.calendar.prev();
    },
    nextDate () {
      this.$refs.calendar.next();
    }
  }
}
</script>