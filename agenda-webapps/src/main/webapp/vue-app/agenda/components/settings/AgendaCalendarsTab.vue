<template>
  <v-list>
    <agenda-calendars-item
      v-for="calendar in calendars"
      :key="calendar"
      :calendar="calendar" />
    <v-flex v-if="hasMore" class="agendaLoadMoreParent d-flex my-4 border-box-sizing">
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="btn mx-auto"
        @click="loadMore">
        {{ $t('agenda.button.loadMore') }}
      </v-btn>
    </v-flex>
  </v-list>
</template>

<script>
export default {
  data: () => ({
    calendars: [],
    loading: false,
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
    loading() {
      if (this.loading) {
        this.$emit('start-loading');
      } else {
        this.$emit('end-loading');
      }
    },
  },
  methods: {
    reset() {
      this.limit = this.pageSize;
    },
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    retrieveCalendars() {
      this.loading = true;
      this.$calendarService.getCalendars(0, this.limit, this.totalSize === 0).then(data => {
        this.calendars = data.calendars;
        this.totalSize = data.size || this.totalSize;
      })
        .finally(() => this.loading = false);
    },
  },
};
</script>