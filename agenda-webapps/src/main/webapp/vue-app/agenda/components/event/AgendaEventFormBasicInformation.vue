<template>
  <v-container ref="agendaEventForm" class="event-form">
    <div class="d-flex flex-row">
      <label class="float-left mt-5 mr-3 text-subtitle-1">Create</label>
      <input
        id="eventTitle"
        ref="eventTitle"
        v-model="event.summary"
        :placeholder="$t('agenda.eventTitle')"
        type="text"
        name="title"
        class="ignore-vuetify-classes my-3"
        required>
      <span class="mt-5  ml-4 mr-4 text-subtitle-1 font-weight-bold">in</span>
      <exo-identity-suggester
        id="calendarOwnerAutocomplete"
        ref="calendarOwnerId"
        v-model="calendarOwner"
        :labels="calendarSuggesterLabels"
        :include-users="false"
        name="calendarOwnerAutocomplete"
        class="user-suggester"
        include-spaces />
    </div>
    <div class="d-flex flex-row mt-1 event-form-body">
      <div class="d-flex flex-column flex-grow-1">
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
          <v-list>
            <v-icon size="18" class="mr-11">
              fas fa-bell
            </v-icon>
            <v-list-item v-for="notifs in notifications" :key="notifs">
              <label class="float-left ml-4 mr-4">{{ $t('agenda.label.notifyUsers') }}</label>
              <input
                ref="timeNotification"
                type="text"
                name="timeNotification"
                class="ignore-vuetify-classes my-3 time-notification">
              <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline notification-date-option">
                <option>{{ $t('agenda.option.minutes') }}</option>
                <option>{{ $t('agenda.option.hours') }}</option>
                <option>{{ $t('agenda.option.days') }}</option>
                <option>{{ $t('agenda.option.weeks') }}</option>
              </select>
              <span class="ml-4">{{ $t('agenda.label.beforeStart') }}</span>
              <v-btn
                color="grey"
                icon
                dark
                @click="removeNotifUser(notifs.id)">
                <v-icon>
                  mdi-close
                </v-icon>
              </v-btn>
            </v-list-item>
            <a class="text-subtitle-1 font-weight-regular add-notification-link" @click="addNotification">{{ $t('agenda.addNotification') }}</a>
          </v-list>
        </div>
        <div class="d-flex flex-row">
          <v-icon size="18" class="mr-11">
            fas fa-redo-alt
          </v-icon>
          <select class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option>1</option>
            <option>2</option>
            <option>3</option>
            <option>4</option>
          </select>
        </div>
        <div class="d-flex flex-row">
          <v-icon size="18" class="mr-12">
            fas fa-file-alt
          </v-icon>
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
          <v-icon size="18" class="mr-11">
            fas fa-paperclip
          </v-icon>
          <v-list-item v-for="attachedFile in files" :key="attachedFile.name">
            <span class="text-subtitle-1 font-weight-regular">{{ attachedFile.name }}.{{ attachedFile.mimeType }}({{ formatFileSize(attachedFile.size) }})</span>
            <v-btn
              color="grey"
              icon
              dark
              @click="removeFile(attachedFile.uploadId)">
              <v-icon>
                mdi-close
              </v-icon>
            </v-btn>
          </v-list-item>
          <agenda-file-attachments :attachments-file="files" @files="files = $event" />
        </div>
      </div>
      <div class="d-flex flex-column mx-5">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column flex-grow-1">
        <div class="d-flex flex-row">
          <v-flex class="flex-grow-0 mr-2">
            <v-icon class="mt-5" size="18">
              fas fa-users
            </v-icon>
          </v-flex>
          <v-flex class="mx-4">
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
                  <v-img :src="attendee.identity.profile.avatarUrl" />
                </v-avatar>
                <span class="text-truncate">
                  {{ attendee.identity.profile && attendee.identity.profile.fullName || attendee.identity.remoteId }}
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
  </v-container>
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
            avatarUrl: this.invitedAttendee.profile.avatarUrl,
            fullName: this.invitedAttendee.profile.fullName,
          },
        }});
      }
      this.invitedAttendee = null;
    },
  },
  mounted(){
    this.$userService.getUser(eXo.env.portal.userName).then(user => {
      this.currentUser = user;
      this.reset();
    });
  },
  methods:{
    reset() {
      if (!this.event.id) { // In case of new event
        // Add current user as default attendee
        if (this.currentUser) {
          this.event.attendees = [{identity: {
            id: eXo.env.portal.userIdentityId,
            providerId: 'organization',
            remoteId: eXo.env.portal.userName,
            profile: {
              avatarUrl: this.currentUser.avatar,
              fullName: this.currentUser.fullname,
            },
          }}];
        } else {
          this.event.attendees = [];
        }
      }
    },
    addNotification() {
      this.notifications.push({});
    },
    removeNotifUser(index) {
      this.notifications = this.notifications.filter((n) => n.id !== index);
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