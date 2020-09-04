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
        v-model="event.calendar.owner.id"
        :labels="calendarSuggesterLabels"
        :include-users="false"
        name="calendarOwnerAutocomplete"
        class="space-suggester"
        include-spaces
        multiple />
    </div>
    <div class="d-flex flex-row">
      <div class="d-flex flex-column">
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
      <div class="d-flex flex-column ml-5">
        <v-divider vertical />
      </div>
      <div class="d-flex flex-column ml-5">
        <div class="d-flex flex-row">
          <v-icon size="18" class="mr-2">
            fas fa-users
          </v-icon>
          <exo-identity-suggester
            ref="autoFocusInput3"
            v-model="invitedMembers"
            :labels="participantSuggesterLabels"
            :disabled="savingUser"
            name="inviteMembers"
            type-of-relations="user_to_invite"
            class="user-suggester"
            include-users
            include-spaces />
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
      savingUser: false,
      invitedMembers: [],
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
  },
  watch: {
    savingUser() {
      if (this.savingUser) {
        this.$refs.agendaEventForm.startLoading();
      } else {
        this.$refs.agendaEventForm.endLoading();
      }
    },
  },
  methods:{
    addNotification() {
      this.notifications.push({
        id: this.nbNotif++
      });
    },
    removeNotifUser(index) {
      this.notifications = this.notifications.filter((n) => n.id !== index);
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