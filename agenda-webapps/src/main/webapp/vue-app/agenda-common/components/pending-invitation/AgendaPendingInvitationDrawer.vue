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
    pendingInvitations: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    drawer: false,
    selectedOwnerIds: [],
  }),
  computed: {
    calendarOwnersSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.filterAgendaPlaceholder'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  watch: {
    selectedOwnerIds() {
      this.applyFilters();
    },
  },
  created() {
    this.$root.$on('agenda-pending-invitation-drawer-open', this.open);
  },
  methods: {
    applyFilters() {
      this.$emit('changed', this.selectedOwnerIds);
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