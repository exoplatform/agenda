<template>
  <exo-drawer ref="calendarPendingDatePolls" right>
    <template slot="title">
      {{ $t('agenda.pendingInvitations') }}
    </template>
    <template slot="content">
      <v-list>
        <agenda-pending-invitation-item
          v-for="datePoll in pendingDatePolls"
          :key="datePoll.id"
          :date-poll="datePoll"
          class="mx-2 px-2 py-0 mb-2"
          min-height="auto"
          min-width="100%"
          @close="close" />
      </v-list>
    </template>
    <template v-if="hasMore" slot="footer">
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="btn mx-auto"
        @click="loadMore">
        {{ $t('agenda.button.loadMore') }}
      </v-btn>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    drawer: false,
    pendingDatePolls: null,
    pendingDatePollsCount: 0,
    pageSize: 20,
    limit: 20,
    loading: false,
  }),
  computed: {
    hasMore() {
      return this.pendingDatePolls && this.pendingDatePolls.length < this.pendingDatePollsCount;
    },
  },
  created() {
    this.$root.$on('agenda-pending-date-polls-drawer-open', this.open);
  },
  methods: {
    close() {
      this.$refs.calendarPendingDatePolls.close();
    },
    open() {
      this.$refs.calendarPendingDatePolls.open();
      this.$nextTick().then(() => {
        this.retrievePendingDatePolls();
      });
    },
    loadMore(){
      this.limit += this.pageSize;
      this.retrievePendingDatePolls();
    },
    retrievePendingDatePolls(){
      this.loading = true;
      this.$refs.calendarPendingDatePolls.startLoading();
      return this.$eventService.getDatePolls(0, this.limit, 'response')
        .then(eventsList => {
          this.pendingDatePollsCount = eventsList.size || 0;
          this.pendingDatePolls = eventsList && eventsList.events || [];
        }).catch(error =>{
          console.error('Error retrieving pending date polls', error);
        }).finally(() => {
          this.loading = false;
          this.$refs.calendarPendingDatePolls.endLoading();
        });
    },
  },
};
</script>