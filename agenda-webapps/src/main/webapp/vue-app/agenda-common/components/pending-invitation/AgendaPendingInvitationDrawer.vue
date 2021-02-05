<template>
  <exo-drawer
    ref="calendarPendingInvitation"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.PendingInvitation') }}
    </template>
    <template slot="content">
      <agenda-pending-invitation-list :pending-invitations="pendingInvitations" />
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    ownerIds: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    initialized: false,
    drawer: false,
    selectedOwnerIds: [],
    pendingInvitations: null,
    hasMore: false,
    settingsLoaded: false,
    pageSize: 10,
    ownerIds: [],
    limit: 0,
    period: {
      start: new Date(),
      end: null,
    },
  }),
  computed: {
    calendarOwnersSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.filterAgendaPlaceholder'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  created() {
    this.$root.$on('agenda-pending-invitation-drawer-open', this.open);
    this.retrievePendingInvitations();
  },
  methods: {
    applyFilters() {
      this.$emit('changed', this.selectedOwnerIds);
    },
    retrievePendingInvitations(){
      this.loading = true;
      const userIdentityId = eXo.env.portal.userIdentityId;
      if (this.ownerIds === false) {
        this.pendingInvitations = [];
        this.hasMore = false;
        this.loading = false;
        this.initialized = true;
        return;
      }
      return this.$eventService.getPendingInvitations(null, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.limit, ['NEEDS_ACTION'], 'attendees')
        .then(data => {
          const pendingInvitations = data && data.events || [];
          pendingInvitations.forEach(event => {
            event.name = event.summary;
          });
          this.hasMore = this.limit > this.pageSize && (this.pendingInvitations && this.pendingInvitations.length || 0) < pendingInvitations.length || pendingInvitations.length >= this.limit;
          this.pendingInvitations = pendingInvitations;
        }).catch(error =>{
          console.error('Error retrieving pending invitations', error);
        }).finally(() => {
          this.initialized = true;
          this.loading = false;
        });
    },
    retrieveDatePolls(){
      this.loading = true;
      const userIdentityId = eXo.env.portal.userIdentityId;
      if (this.ownerIds === false) {
        this.pendingInvitations = [];
        this.hasMore = false;
        this.loading = false;
        this.initialized = true;
        return;
      }
      return this.$eventService.getEvents(null, this.ownerIds, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.$agendaUtils.toRFC3339(this.period.end), this.limit, null, 'all', 'TENTATIVE')
        .then(data => {
          const pendingInvitations = data && data.events || [];
          pendingInvitations.forEach(event => {
            event.name = event.summary;
          });
          this.hasMore = this.limit > this.pageSize && (this.pendingInvitations && this.pendingInvitations.length || 0) < pendingInvitations.length || pendingInvitations.length >= this.limit;
          this.pendingInvitations = pendingInvitations;
        }).catch(error =>{
          console.error('Error retrieving pending invitations', error);
        }).finally(() => {
          this.initialized = true;
          this.loading = false;
        });
    },
    close() {
      this.$refs.calendarPendingInvitation.close();
    },
    open() {
      this.$refs.calendarPendingInvitation.open();
      this.$nextTick().then(() => {
        //this.$refs.PendingInvitationList.reset();
      });
    },
  },
};
</script>