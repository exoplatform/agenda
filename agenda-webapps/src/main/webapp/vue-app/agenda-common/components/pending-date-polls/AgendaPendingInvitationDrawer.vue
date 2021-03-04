<template>
  <exo-drawer ref="pendingInvitationDrawer" right>
    <template slot="title">
      {{ $t('agenda.pendingInvitations') }}
    </template>
    <template slot="content">
      <v-list>
        <agenda-pending-invitation-item
          v-for="invitedEvent in pendingInvitations"
          :key="invitedEvent.id"
          :invited-event="invitedEvent"
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
  props: {
    currentSpace: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    pendingDatePolls: [],
    pendingDatePollsCount: 0,
    pendingEvents: [],
    pendingEventCount: 0,
    pageSize: 10,
    limit: 10,
    loading: 0,
  }),
  computed: {
    spaceIdentityId() {
      return this.currentSpace && this.currentSpace.identity && this.currentSpace.identity.id;
    },
    pendingInvitations() {
      const pendingInvitations = [...this.pendingEvents, ...this.pendingDatePolls];
      pendingInvitations.sort((event1, event2) => {
        const date1 = event1.updated || event1.created;
        const date2 = event2.updated || event2.created;
        return date2.localeCompare(date1);
      });
      return pendingInvitations.slice(0, this.limit);
    },
    pendingInvitationsCount() {
      return this.pendingDatePollsCount + this.pendingEventCount;
    },
    displayedPendingInvitationsCount() {
      return this.pendingInvitations.length;
    },
    hasMore() {
      return this.pendingInvitationsCount > this.displayedPendingInvitationsCount;
    },
  },
  watch: {
    loading() {
      if (this.loading) {
        this.$refs.pendingInvitationDrawer.startLoading();
      } else {
        this.$refs.pendingInvitationDrawer.endLoading();
      }
    },
  },
  created() {
    this.$root.$on('agenda-pending-date-polls-drawer-open', this.open);
  },
  methods: {
    close() {
      this.$refs.pendingInvitationDrawer.close();
    },
    open() {
      this.retrievePendingInvitations();
      this.$refs.pendingInvitationDrawer.open();
    },
    loadMore(){
      this.limit += this.pageSize;
      this.retrievePendingInvitations();
    },
    retrievePendingInvitations() {
      this.retrievePendingEvents();
      this.retrieveDatePolls();
    },
    retrieveDatePolls() {
      this.loading++;
      return this.$eventService.getDatePolls(this.spaceIdentityId, 0, this.limit, 'response')
        .then(eventsList => {
          this.pendingDatePollsCount = eventsList.size || 0;
          this.pendingDatePolls = eventsList && eventsList.events || [];
        }).catch(error =>{
          console.error('Error retrieving pending date polls', error);
        }).finally(() => this.loading--);
    },
    retrievePendingEvents() {
      this.loading++;
      return this.$eventService.getPendingEvents(this.spaceIdentityId, 0, this.limit)
        .then(eventsList => {
          this.pendingEventCount = eventsList.size || 0;
          this.pendingEvents = eventsList && eventsList.events || [];
        }).catch(error =>{
          console.error('Error retrieving pending date polls', error);
        }).finally(() => this.loading--);
    },
  },
};
</script>