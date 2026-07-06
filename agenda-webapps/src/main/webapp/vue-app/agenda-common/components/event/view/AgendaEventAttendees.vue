<template>
  <div class="d-flex align-center full-width">
    <v-icon size="20" class="icon-default-color me-2 flex-shrink-0">fas fa-users</v-icon>
    <div class="d-flex align-center ms-7 flex-grow-1 attendee-status-badges">
      <v-tooltip v-if="acceptedResponsesCount" bottom>
        <template #activator="{ on }">
          <v-badge
            :content="acceptedResponsesCount"
            :value="acceptedResponsesCount"
            color="#F8B121"
            offset-x="9"
            offset-y="8"
            class="me-9">
            <v-btn icon x-small v-on="on" @click="openDrawer">
              <v-icon size="20" color="success">fas fa-check-circle</v-icon>
            </v-btn>
          </v-badge>
        </template>
        <span>{{ $t('agenda.accepted') }}: {{ acceptedResponsesCount }}</span>
      </v-tooltip>
      <v-tooltip v-if="needsActionResponsesCount" bottom>
        <template #activator="{ on }">
          <v-badge
            :content="needsActionResponsesCount"
            :value="needsActionResponsesCount"
            color="#F8B121"
            offset-x="9"
            offset-y="8"
            class="me-9">
            <v-btn icon x-small v-on="on" @click="openDrawer">
              <v-icon size="20" color="blue">fas fa-question-circle</v-icon>
            </v-btn>
          </v-badge>
        </template>
        <span>{{ $t('agenda.needs_action') }}: {{ needsActionResponsesCount }}</span>
      </v-tooltip>
      <v-tooltip v-if="refusedResponsesCount" bottom>
        <template #activator="{ on }">
          <v-badge
            :content="refusedResponsesCount"
            :value="refusedResponsesCount"
            color="#F8B121"
            offset-x="9"
            offset-y="8"
            class="me-9">
            <v-btn icon x-small v-on="on" @click="openDrawer">
              <v-icon size="20" color="error">fas fa-times-circle</v-icon>
            </v-btn>
          </v-badge>
        </template>
        <span>{{ $t('agenda.declined') }}: {{ refusedResponsesCount }}</span>
      </v-tooltip>
    </div>
    <agenda-event-attendees-avatars
      v-if="displayedAttendees.length"
      :attendees="displayedAttendees"
      :max="3"
      :size="32"
      @open="openDrawer" />
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      :editable="canEdit"
      @closed="saveAttendeesIfEditable" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    canEdit() {
      return !!(this.event && this.event.acl && this.event.acl.canEdit);
    },
    attendees() {
      return this.event && this.event.attendees || [];
    },
    creatorAttendee() {
      if (!this.event || !this.attendees || !this.attendees.length) {
        return null;
      }
      return this.attendees.find(attendee => attendee.identity.id === this.event.creator.id);
    },
    creatorAttendeeResponse() {
      return this.creatorAttendee && this.creatorAttendee.response;
    },
    participatingAttendees() {
      if (!this.creatorAttendee) {
        return this.attendees;
      }
      return this.attendees.filter(attendee => attendee.identity.id !== this.creatorAttendee.identity.id);
    },
    participatingUserAttendees() {
      return this.participatingAttendees.filter(attendee => attendee.identity.profile);
    },
    participatingSpaceAttendees() {
      return this.participatingAttendees
        .filter(attendee => attendee.identity.space)
        .sort(this.sortAttendees);
    },
    acceptedResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'ACCEPTED')
        .sort(this.sortAttendees);
    },
    tentativeResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'TENTATIVE')
        .sort(this.sortAttendees);
    },
    refusedResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'DECLINED')
        .sort(this.sortAttendees);
    },
    needsActionResponses() {
      return this.participatingUserAttendees
        .filter(attendee => attendee && attendee.response === 'NEEDS_ACTION')
        .sort(this.sortAttendees);
    },
    acceptedResponsesCount() {
      return this.acceptedResponses.length + (this.creatorAttendeeResponse === 'ACCEPTED' && 1 || 0);
    },
    tentativeResponsesCount() {
      return this.tentativeResponses.length + (this.creatorAttendeeResponse === 'TENTATIVE' && 1 || 0);
    },
    refusedResponsesCount() {
      return this.refusedResponses.length + (this.creatorAttendeeResponse === 'DECLINED' && 1 || 0);
    },
    needsActionResponsesCount() {
      return this.needsActionResponses.length + (this.creatorAttendeeResponse === 'NEEDS_ACTION' && 1 || 0);
    },
    displayedAttendees() {
      const others = this.participatingAttendees.slice().sort(this.sortAttendees);
      return [this.creatorAttendee, ...others].filter(Boolean);
    },
    visibleIdentities() {
      return this.displayedAttendees.slice(0, 3).map(a => {
        const identity = a.identity;
        const profile = identity.profile || identity.space || {};
        return {
          ...identity,
          username: identity.remoteId,
          fullname: profile.fullname || profile.fullName || profile.displayName || identity.remoteId,
          avatar: profile.avatar || profile.avatarUrl,
        };
      });
    },
  },
  data() {
    return {
      attendeesSnapshot: null,
    };
  },
  methods: {
    openDrawer() {
      this.attendeesSnapshot = (this.event && this.event.attendees || [])
        .map(a => a.identity.remoteId).sort().join(',');
      this.$refs.attendeesDrawer.open();
    },
    saveAttendeesIfEditable() {
      if (!this.canEdit || !this.event) {
        return;
      }
      const current = (this.event.attendees || [])
        .map(a => a.identity.remoteId).sort().join(',');
      if (current !== this.attendeesSnapshot) {
        this.$eventService.updateEvent(this.event);
      }
    },
    sortAttendees(attendee1, attendee2) {
      const displayName1 = (attendee1.identity.profile && attendee1.identity.profile.fullname)
        || (attendee1.identity.space && attendee1.identity.space.displayName) || '';
      const displayName2 = (attendee2.identity.profile && attendee2.identity.profile.fullname)
        || (attendee2.identity.space && attendee2.identity.space.displayName) || '';
      return displayName1.localeCompare(displayName2);
    },
  },
};
</script>