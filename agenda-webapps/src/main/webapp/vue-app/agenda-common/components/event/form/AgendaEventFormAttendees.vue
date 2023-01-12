<template>
  <v-flex class="user-suggester text-truncate">
    <form
      ref="form"
      @keypress="checkGuestInvitation($event)">
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
    </form>
    <agenda-notification-alerts v-if="displayAlert" name="event-form" />
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
      if (!this.event.calendar || !this.event.calendar.owner || !this.event.calendar.owner.remoteId) {
        return true;
      } else {
        return false;
      }
    },
    suggesterStatus(){
      if (this.disableAttendeeSuggester) {
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
  methods: {
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
              external: this.currentUser.external === 'true',
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
    checkGuestInvitation(evt) {
      const self=this;
      $('form').on('focusout', function(event) {
        setTimeout(function() {
          if (!event.delegateTarget.contains(document.activeElement)) {
            self.saveGuestEmail(event);
          }
        }, 1);
      });
      // eslint-disable-next-line eqeqeq
      if (evt.key == 'Enter') {
        evt.preventDefault();
        this.saveGuestEmail(evt);   }
      // eslint-disable-next-line eqeqeq
      if (evt.keyCode == '32') {
        this.saveGuestEmail(evt);
      }
    },
    saveGuestEmail() {
      const reg = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}\.[\d]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,24}))$/;
      const input = this.$refs.invitedAttendeeAutoComplete.searchTerm;
      const words = input!== null ? input.split(' ') : '';
      const email = words[words.length - 1];
      if (reg.test(email)) {
        this.event.attendees.push({identity: {
          id: `${email}`,
          remoteId: email,
          identityId: email,
          providerId: 'GUEST_USER',
          profile: {
            fullName: email,
            avatarUrl: '/portal/rest/v1/social/users/default-image/avatar',
          },
        }});
      }
      this.$refs.invitedAttendeeAutoComplete.clear();
    },
  }
};
</script>
