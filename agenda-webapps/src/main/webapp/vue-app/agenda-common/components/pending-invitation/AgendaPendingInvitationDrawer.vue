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
      <div class="d-flex mx-4">
        <v-btn
          :loading="loading"
          :disabled="loading"
          class="btn mx-auto"
          @click="loadMore">
          {{ $t('agenda.button.loadMore') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    pendingDatePolls: null,
    pendingDatePollsCount: 0,
    pageSize: 20,
    limit: 20,
    loading: false,
    ownerIds:[],
    currentSpace:null,
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
        this.retrieveDatePolls();
      });
    },
    loadMore(){
      this.limit += this.pageSize;
      this.retrieveDatePolls();
    },
    retrieveDatePolls() {
      if (eXo.env.portal.spaceId) {
        const spaceId = eXo.env.portal.spaceId;
        this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
            }
            this.retrieveDatePollsFromStore(this.ownerIds);
          });
      } else {
        this.retrieveDatePollsFromStore(this.ownerIds);
      }
    },
    retrieveDatePollsFromStore(ownerIds) {
      this.loading = true;
      let spaceIds = [];
      if(ownerIds && ownerIds.length > 0) {
        spaceIds.push(ownerIds);
      } else {
        spaceIds = [];
      }
      this.$refs.calendarPendingDatePolls.startLoading();
      return this.$eventService.getDatePolls(spaceIds, 0, this.limit, 'response')
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