<template>
  <v-flex ref="agendaEventForm">
    <div class="d-flex flex-column flex-md-row">
      <label class="float-left mt-5 mr-3 text-subtitle-1 d-none d-md-inline">Create</label>
      <input
        id="eventTitle"
        ref="eventTitle"
        v-model="event.summary"
        :placeholder="$t('agenda.eventTitle')"
        type="text"
        name="title"
        class="ignore-vuetify-classes my-3"
        required>
      <span class="mt-5 ml-4 mr-4 text-subtitle-1 font-weight-bold d-none d-md-inline">in</span>
      <exo-identity-suggester
        id="calendarOwnerAutocomplete"
        ref="calendarOwner"
        v-model="calendarOwner"
        :labels="calendarSuggesterLabels"
        :include-users="false"
        name="calendarOwnerAutocomplete"
        class="user-suggester"
        include-spaces />
    </div>
    <div class="d-flex flex-column flex-md-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1 event-form-body-left">
        <div class="d-flex flex-row">
          <v-icon size="18" class="mr-11">
            fas fa-map-marker-alt
          </v-icon>
          <input
            id="eventLocation"
            ref="eventLocation"
            v-model="event.location"
            :placeholder="$t('agenda.eventLocation')"
            type="text"
            name="locationEvent"
            class="ignore-vuetify-classes my-3 location-event-input"
            required>
        </div>
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <v-icon size="18" class="my-5 mr-11">
              fas fa-bell
            </v-icon>
          </v-flex>
          <agenda-event-form-reminders :event="event" />
        </div>
        <div class="d-flex flex-row">
          <v-icon size="18" class="mr-11">
            fas fa-redo-alt
          </v-icon>
          <select class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option>{{ $t('agenda.doNotRepeat') }}</option>
          </select>
        </div>
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <v-icon size="18" class="my-5 mr-11">
              fas fa-file-alt
            </v-icon>
          </v-flex>
          <textarea
            id="eventDescription"
            ref="eventDescription"
            v-model="event.description"
            :placeholder="$t('agenda.description')"
            type="text"
            name="description"
            rows="20"
            maxlength="2000"
            noresize
            class="ignore-vuetify-classes my-3 description-event-textarea">
          </textarea>
        </div>
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0">
            <v-icon size="18" class="my-5 mr-11">
              fas fa-paperclip
            </v-icon>
          </v-flex>
          <agenda-event-form-attachments :event="event" />
        </div>
      </div>
      <div class="d-none d-md-flex flex-column mx-5 event-form-body-divider ">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column flex-grow-1 event-form-body-right">
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0 mr-2">
            <v-icon class="mt-5" size="18">
              fas fa-users
            </v-icon>
          </v-flex>
          <v-flex class="ml-4">
            <exo-identity-suggester
              ref="invitedAttendeeAutoComplete"
              v-model="invitedAttendee"
              :labels="participantSuggesterLabels"
              :disabled="savingUser"
              :search-options="{
                currentUser: '',
              }"
              :ignore-items="ignoredMembers"
              name="inviteAttendee"
              class="user-suggester"
              include-users
              include-spaces />
            <div v-if="event.attendees" class="identitySuggester no-border mt-0">
              <v-chip
                v-for="attendee in event.attendees"
                :key="attendee.identity.id"
                close
                class="identitySuggesterItem mr-4 mt-4"
                @click:close="removeAttendee(attendee)">
                <v-avatar v-if="attendee.identity.profile" left>
                  <v-img :src="attendee.identity.profile.avatar" />
                </v-avatar>
                <v-avatar v-else-if="attendee.identity.space" left>
                  <v-img :src="attendee.identity.space.avatarUrl" />
                </v-avatar>
                <span v-if="attendee.identity.profile" class="text-truncate">
                  {{ attendee.identity.profile && attendee.identity.profile.fullname || attendee.identity.remoteId }}
                </span>
                <span v-else-if="attendee.identity.space" class="text-truncate">
                  {{ attendee.identity.space && attendee.identity.space.displayName || attendee.identity.remoteId }}
                </span>
              </v-chip>
            </div>
          </v-flex>
        </div>
        <div class="d-flex flex-row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch v-model="enablePermission" class="mt-0 ml-4" />
        </div>
        <div class="d-flex flex-row font-weight-regular">
          {{ $t('agenda.modifyEventPermissionDescription') }}
        </div>
        <div class="d-flex flex-row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.enableInvitation') }}</label>
          <v-switch v-model="enableInvitation" class="mt-0 ml-4" />
        </div>
        <div class="d-flex flex-row font-weight-regular">
          {{ $t('agenda.enableInvitationDescription') }}
        </div>
      </div>
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
      files:[],
      currentUser: null,
      savingUser: false,
      calendarOwner: null,
      invitedAttendee: [],
      notifications: [],
      nbNotif: 0,
      enablePermission: false,
      enableInvitation: false,
    };
  },
  computed: {
    participantSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.addParticipants'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    calendarSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.chooseCalendar'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
    ignoredMembers() {
      return this.event.attendees.flatMap(attendee => [attendee.id, `${attendee.identity.providerId}:${attendee.identity.remoteId}`]);
    },
  },
  watch: {
    savingUser() {
      if (this.savingUser) {
        this.$refs.agendaEventForm.startLoading();
      } else {
        this.$refs.agendaEventForm.endLoading();
      }
    },
    currentUser() {
      this.reset();
    },
    calendarOwner() {
      if (this.calendarOwner) {
        this.event.calendar.owner = {
          remoteId: this.calendarOwner.remoteId,
          providerId: this.calendarOwner.providerId,
        };
      } else {
        this.event.calendar.owner = null;
      }
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
        this.event.attendees.push({identity: {
          remoteId: this.invitedAttendee.remoteId,
          providerId: this.invitedAttendee.providerId,
          profile: {
            avatar: this.invitedAttendee.profile.avatarUrl,
            fullname: this.invitedAttendee.profile.fullName,
          },
        }});
      }
      this.invitedAttendee = null;
    },
  },
  mounted(){
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.currentUser = user;
    });
  },
  methods:{
    validateForm() {

      
      
      
      // TODO form validation

      
      return true;
    },
    reset() {
      if (this.event.id) { // In case of new event
        const owner = this.event.calendar.owner;
        this.calendarOwner = {
          id: `${owner.providerId}:${owner.remoteId}`,
          remoteId: owner.remoteId,
          providerId: owner.providerId,
          profile: {
            avatarUrl: owner.profile && owner.profile.avatar || owner.space && owner.space.avatarUrl || '',
            fullName: owner.profile && owner.profile.fullName || owner.space && owner.space.displayName || '',
          },
        };
        this.$refs.calendarOwner.items = [this.calendarOwner];
      } else { // In case of new event
        this.$refs.calendarOwner.items = [];
        this.calendarOwner = {};

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
    },
    addReminder() {
      this.event.reminders.push({
        before: 10,
        beforePeriodType: 'MINUTE',
      });
    },
    removeReminder(index) {
      this.event.reminders.splice(index, 1);
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
    removeFile(index) {
      this.files = this.files.filter((n) => n.uploadId !== index);
    },
    formatFileSize(size) {
      return this.$agendaUtils.getFormattedFileSize(size);
    }
  }
};
</script>