<template>
  <div class="d-flex align-center flex-grow-1">
    <span class="me-4">{{ $t('agenda.participants') }}</span>
    <v-btn
      :class="sortedAttendees.length > 1 && !event.id || event.id ? '' : 'ms-auto'"
      :title="$t('agenda.addParticipants')"
      icon
      small
      @click="openDrawer">
      <v-icon class="icon-default-color" size="18">fas fa-plus</v-icon>
    </v-btn>
    <div
      v-if="sortedAttendees.length > 1 && !event.id || event.id"
      class="ms-auto d-flex align-center">
      <agenda-event-attendees-avatars
        v-if="!showSeeMore"
        :attendees="sortedAttendees"
        :max="3"
        :size="34" 
        @open="openDrawer" />
    </div>
    <agenda-event-form-attendees-drawer
      ref="attendeesDrawer"
      :event="event"
      @toggle-open="toggleOpen"
      @change-capacity="changeCapacity"
      @initialized="$emit('initialized')" />
    <agenda-capacity-close-confirm-dialog
      v-if="confirmClosing"
      ref="closeConfirm" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    /**
     * Whether locking the event is confirmed at the padlock, for a host whose
     * save asks nothing (D15): the event of an article, when it was loaded open
     * with a limit. The server asks from the event as stored, so a limit the
     * stepper removed since does not spare the question. The event form asks
     * when it saves instead.
     */
    confirmClosing: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
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
    /**
     * In the event form the padlock is a form value: it is saved with the
     * event, so an event can be created already open and no call is made here
     * (the detail page patches instead, where the event already exists). On a
     * date of a series it travels through the scope dialog like every other
     * field of the form, which is what makes it answerable per date (US06).
     * Where the host asks nothing when it saves, locking an event loaded open
     * with a limit is confirmed here first, the host told once it is.
     *
     * @returns {void}
     */
    toggleOpen() {
      const open = !this.event.open;
      if (!open && this.confirmClosing) {
        this.$refs.closeConfirm.confirm()
          .then(() => {
            this.setOpen(false);
            this.$emit('closing-confirmed');
          })
          .catch(() => null);
        return;
      }
      this.setOpen(open);
    },
    setOpen(open) {
      this.$set(this.event, 'open', open);
      if (open) {
        // An open event excludes both participant permissions: the drawer
        // blocks the padlock while "can modify" is on, and "can invite" — set
        // by its watcher, never shown — is cleared here, as the event page
        // clears it in its patch
        this.$set(this.event, 'allowAttendeeToUpdate', false);
        this.$set(this.event, 'allowAttendeeToInvite', false);
      }
    },
    /**
     * The limit is a form value like the padlock: saved with the event, and on
     * a date of a series through the scope dialog of the save.
     *
     * @param {Number} capacity the limit chosen, 0 for none
     * @returns {void}
     */
    changeCapacity(capacity) {
      this.$set(this.event, 'capacity', capacity);
    },
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