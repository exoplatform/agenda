<template>
  <div class="d-flex align-center full-width">
    <v-icon size="20" class="icon-default-color me-2 flex-shrink-0">fas fa-users</v-icon>
    <div class="d-flex align-center ms-6 flex-grow-1 attendee-status-badges">
      <v-badge
        v-if="acceptedResponsesCount"
        :content="acceptedResponsesCount"
        :value="acceptedResponsesCount"
        offset-x="13"
        color="warning-color-background"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.accepted')"
          @click="openDrawer('ACCEPTED')">
          <v-icon size="20" class="success-color">fas fa-check-circle</v-icon>
        </v-btn>
      </v-badge>
      <v-badge
        v-if="tentativeResponsesCount"
        :content="tentativeResponsesCount"
        :value="tentativeResponsesCount"
        color="warning-color-background"
        offset-x="13"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.tentative')"
          @click="openDrawer('TENTATIVE')">
          <v-icon size="20" class="primary--text">fas fa-question-circle</v-icon>
        </v-btn>
      </v-badge>
      <v-badge
        v-if="refusedResponsesCount"
        :content="refusedResponsesCount"
        :value="refusedResponsesCount"
        color="warning-color-background"
        offset-x="13"
        offset-y="12"
        class="me-8">
        <v-btn
          icon
          small
          :title="$t('agenda.filter.button.title.declined')"
          @click="openDrawer('DECLINED')">
          <v-icon size="20" class="error-color">fas fa-times-circle</v-icon>
        </v-btn>
      </v-badge>
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
      applies-on-series
      @toggle-open="toggleOpen"
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
      togglingOpen: false,
    };
  },
  methods: {
    /**
     * Opens or locks the event from the detail page, where the event exists and
     * the change applies at once (the form host saves it with the form
     * instead).
     *
     * The flag belongs to the series, so the patch targets the parent when an
     * occurrence is displayed; the local event is updated only once the server
     * accepted, and the parent with it, since the drawer and the answer buttons
     * read the effective value from either object.
     *
     * updateAllOccurrences stays false on purpose: with true, the service
     * deletes every exceptional occurrence of the series (the date-change
     * save passes !!recurrence because it means to). The flag is read on the
     * series, so nothing needs to be propagated.
     *
     * @returns {void}
     */
    toggleOpen() {
      if (!this.event || this.togglingOpen) {
        return;
      }
      const seriesId = this.event.parent && this.event.parent.id || this.event.id;
      if (!seriesId) {
        return;
      }
      const open = !this.event.open;
      this.togglingOpen = true;
      this.$eventService.updateEventFields({id: seriesId}, {open}, false, false)
        .then(() => {
          this.$set(this.event, 'open', open);
          if (this.event.parent) {
            this.$set(this.event.parent, 'open', open);
          }
        })
        .catch(error => {
          // The server refuses with a message code (a stale page: the event was
          // moved to a personal calendar or turned into a date poll meanwhile);
          // shown when the bundle knows it, the generic text otherwise
          const code = error && error.code;
          const message = code && this.$te(code) ? this.$t(code) : this.$t('agenda.openEvent.updateError');
          this.$root.$emit('alert-message', message, 'error');
        })
        .finally(() => this.togglingOpen = false);
    },
    openDrawer(responseFilter) {
      this.attendeesSnapshot = (this.event && this.event.attendees || [])
        .map(a => a.identity.remoteId).sort().join(',');
      this.$refs.attendeesDrawer.open(responseFilter);
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