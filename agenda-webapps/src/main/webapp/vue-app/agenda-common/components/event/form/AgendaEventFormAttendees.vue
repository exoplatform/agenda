<template>
  <div class="d-flex align-center flex-grow-1">
    <span class="me-4">{{ $t('agenda.participants') }}</span>
    <v-btn
      :title="$t('agenda.addParticipants')"
      class="flex-shrink-0"
      icon
      small
      @click="openDrawer">
      <v-icon class="icon-default-color" size="18">fas fa-plus</v-icon>
    </v-btn>
    <div
      v-if="displayAttendeesAvatars"
      class="ms-auto d-flex align-center">
      <agenda-event-attendees-avatars
        :attendees="sortedAttendees"
        :max="3"
        :size="34"
        @open="openDrawer" />
    </div>
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      @initialized="$emit('initialized')" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  computed: {
    displayAttendeesAvatars() {
      return !!this.event.id || this.sortedAttendees.length > 1;
    },
    sortedAttendees() {
      if (!this.event.attendees) { return []; }
      const creatorId = this.event.creator && this.event.creator.id;
      const ownerId = creatorId || eXo.env.portal.userIdentityId;
      const owner = this.event.attendees.find(a => Number(a.identity.id) === Number(ownerId));
      const others = this.event.attendees
        .filter(a => a !== owner)
        .sort((a, b) => (this.getDisplayName(a) || '').localeCompare(this.getDisplayName(b) || ''));
      return owner ? [owner, ...others] : others;
    },
  },
  mounted() {
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.$root.$emit('current-user', user);
      if (!this.event.id && !this.event.occurrence && (!this.event.attendees || !this.event.attendees.length)) {
        this.event.attendees = [{
          identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatar: user.avatar,
              fullname: user.fullname,
              external: user.external === 'true',
            },
          },
        }];
      }
      this.$emit('initialized');
    });
  },
  methods: {
    openDrawer() {
      this.$refs.attendeesDrawer.open();
    },
    getDisplayName(attendee) {
      const profile = attendee.identity && (attendee.identity.profile || attendee.identity.space);
      return profile && (profile.displayName || profile.fullname || profile.fullName) || attendee.identity.remoteId;
    },
  },
};
</script>