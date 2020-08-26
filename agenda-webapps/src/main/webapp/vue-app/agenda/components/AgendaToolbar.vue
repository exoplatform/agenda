<template>
  <div>
    <v-toolbar flat color="white">
      <v-btn
        outlined
        class="mr-4"
        color="grey darken-2"
        @click="setToday">
        Today
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
      <v-toolbar-title v-if="$refs.calendar">
        {{ $refs.calendar.title }}
      </v-toolbar-title>
      <v-spacer />
    </v-toolbar>
  </div>
</template>
<script>
export default {
  data: () => ({
    focus: '',
  }),
  methods: {
    setToday () {
      this.focus = '';
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