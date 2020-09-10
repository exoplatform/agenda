<template>
  <v-list>
    <agenda-calendars-item
      v-for="calendar in calendars"
      :key="calendar"
      :calendar="calendar" />
  </v-list>
</template>

<script>
export default {
  data: () => ({
    calendars: [],
    limit: 0,
    pageSize: 20,
    totalSize: 0,
  }),
  computed: {
    hasMore() {
      return this.limit < this.totalSize;
    },
  },
  watch: {
    limit() {
      this.retrieveCalendars();
    },
  },
  created() {
    this.limit = this.pageSize;
  },
  methods: {
    nextPage() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    retrieveCalendars() {
      this.$calendarService.getCalendars(0, this.limit).then(data => {
        this.calendars = data.calendars;
        this.totalSize = data.size;
      });
    },
  },
};
</script>