<template>
  <v-flex class="user-suggester text-truncate">
    <exo-identity-suggester
      ref="invitedAttendeeAutoComplete"
      v-model="invitedAttendee"
      :labels="participantSuggesterLabels"
      :title="suggesterStatus"
      :disabled="disableAttendeeSuggester"
      :ignore-items="ignoredMembers"
      :search-options="searchOptions"
      name="inviteAttendee"
      no-redactor-space
      include-users
      include-spaces />
    <span v-if="disableAttendeeSuggester" class="error--text">
      {{ $t('agenda.suggesterRequired') }}
    </span>
    <div v-if="event.attendees" class="identitySuggester no-border mt-0">
      <agenda-event-form-attendee-item
        v-for="attendee in event.attendees"
        :key="attendee.identity.id"
        :attendee="attendee"
        :creator="event.creator"
        @remove-attendee="removeAttendee" />
    </div>
  </v-flex>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
  },
  data() {
    return {
      currentUser: null,
      invitedAttendee: [],
    };
  },
  computed: {
    searchOptions() {
      return {
        currentUser: '',
        spaceURL: this.event
          && this.event.calendar
          && this.event.calendar.owner
          && this.event.calendar.owner.remoteId,
      };
    },
    participantSuggesterLabels() {
      return {
        searchPlaceholder: this.$t('agenda.searchPlaceholder'),
        placeholder: this.$t('agenda.addParticipants'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    ignoredMembers() {
      return this.event.attendees.map(attendee => `${attendee.identity.providerId}:${attendee.identity.remoteId}`);
    },
    disableAttendeeSuggester() {
      if(!this.event.calendar || !this.event.calendar.owner || !this.event.calendar.owner.remoteId) {
        return true;
      } else {
        return false;
      }
    },
    suggesterStatus(){
      if(this.disableAttendeeSuggester) {
        return this.$t('agenda.suggesterRequired.tooltip');
      } else {
        return '';
      }
    }
  },
  watch: {
    currentUser() {
      this.reset();
    },
    invitedAttendee() {
      if (!this.invitedAttendee) {
        this.$nextTick(this.$refs.invitedAttendeeAutoComplete.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      if (!this.event.attendees) {
        this.event.attendees = [];
      }

      const found = this.event.attendees.find(attendee => {
        return attendee.identity.remoteId === this.invitedAttendee.remoteId
          && attendee.identity.providerId === this.invitedAttendee.providerId;
      });
      if (!found) {
        this.event.attendees.push({
          identity: this.$suggesterService.convertSuggesterItemToIdentity(this.invitedAttendee),
        });
      }
      this.invitedAttendee = null;
    },
  },
  mounted(){
    this.reset();
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.currentUser = user;
      this.$root.$emit('current-user',this.currentUser);
    });
  },
  methods:{
    reset() {
      if (!this.event.id && !this.event.occurrence && (!this.event.attendees || !this.event.attendees.length)) { // In case of edit existing event
        // Add current user as default attendee
        if (this.currentUser) {
          this.event.attendees = [{identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatar: this.currentUser.avatar,
              fullname: this.currentUser.fullname,
            },
          }}];
        } else {
          this.event.attendees = [];
        }
      }
      this.$emit('initialized');
    },
    removeAttendee(attendee) {
      const index = this.event.attendees.findIndex(addedAttendee => {
        return attendee.identity.remoteId === addedAttendee.identity.remoteId
        && attendee.identity.providerId === addedAttendee.identity.providerId;
      });
      if (index >= 0) {
        this.event.attendees.splice(index, 1);
      }
    },
  }
};
</script>