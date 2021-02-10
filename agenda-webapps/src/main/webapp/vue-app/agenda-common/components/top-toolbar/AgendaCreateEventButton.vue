<template>
  <div class="text-no-wrap">
    <v-btn
      class="btn btn-primary"
      @click="openNewEventForm">
      <v-icon dark>
        mdi-plus
      </v-icon>
      <span class="ml-2 d-none d-lg-inline">
        {{ $t('agenda.button.addEvent') }}
      </span>
    </v-btn>
    <v-badge
      v-if="datePollsCount"
      :content="datePollsCount"
      offset-y="10"
      class="d-none d-md-inline"
      color="#F8B121">
      <v-btn
        :title="$t('agenda.pendingInvitations')"
        class="ml-4 mr-2"
        color="white"
        icon
        depressed
        x-small
        @click="$root.$emit('agenda-pending-date-polls-drawer-open')">
        <i class="uiIcon darkGreyIcon uiIcon32x32 uiIconClock mb-1"></i>
      </v-btn>
    </v-badge>
  </div>
</template>
<script>
export default {
  data: () => {
    return {
      datePollsCount: 0,
      ownerIds:[],
      currentSpace: null,
    };
  },
  created() {
    this.$root.$on('agenda-refresh', this.getInComingDatePolls);
    this.$root.$on('agenda-event-saved', this.getInComingDatePolls);
    this.getInComingDatePolls();
  },
  methods: {
    getInComingDatePolls() {
      if (eXo.env.portal.spaceId) {
        const spaceId = eXo.env.portal.spaceId;
        this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
            }
            this.refresh(this.ownerIds);
          });
      } else {
        this.refresh(this.ownerIds);
      }
    },
    refresh(ownerIds) {
      if(ownerIds && ownerIds.length > 0) {
        return this.$eventService.getDatePolls(ownerIds).then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
      } else {
        return this.$eventService.getDatePolls().then(eventsList => this.datePollsCount = eventsList && eventsList.size || 0);
      }
    },
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
  },
};
</script>
