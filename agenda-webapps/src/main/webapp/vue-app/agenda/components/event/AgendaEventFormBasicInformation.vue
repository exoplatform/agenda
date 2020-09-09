<template>
  <v-flex ref="agendaEventForm">
    <v-alert
      v-if="error"
      type="error"
      class="text-center">
      {{ fieldError }}
    </v-alert>
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
          <select
            v-model="eventRecurrence"
            class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option value="NO REPEAT">{{ $t('agenda.doNotRepeat') }}</option>
            <option value="DAILY">{{ $t('agenda.daily') }}</option>
            <option value="WEEKLY">{{ $t('agenda.weekly',{ 0 : dayNamefromDate }) }}</option>
            <option value="MONTHLY">{{ $t('agenda.monthly',{ 0 : dayNamefromDate }) }}</option>
            <option value="YEARLY">{{ $t('agenda.annually',{ 0 : monthFromDate, 1: dayNumberFromDate}) }}</option>
            <option value="">{{ $t('agenda.everyWeekDay') }}</option>
            <option value="">{{ $t('agenda.custom') }}</option>
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
    currentSpace: {
      type: Object,
      default: () => null,
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
      error: null,
      fieldError: '',
      eventRecurrence: '',
    };
  },
  computed: {
    dayNamefromDate () {
      const day = this.event.start;
      return this.$agendaUtils.getDayNameFromDate(day);
    },
    monthFromDate(){
      const day = this.event.start;
      return this.$agendaUtils.getMonthFromDate(day);
    },
    dayNumberFromDate() {
      const day = this.event.start;
      return this.$agendaUtils.getDayNumberFromDate(day);
    },
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
    eventRecurrence() {
      if (this.eventRecurrence && this.eventRecurrence === 'DAILY') {
        this.event.recurrence = {
          frequency: this.eventRecurrence,
          interval: 1
        };
      } else if (this.eventRecurrence && this.eventRecurrence === 'NO REPEAT') {
        this.event.recurrence = null;
      } else if (this.eventRecurrence && this.eventRecurrence === 'WEEKLY') {
        this.event.recurrence = {
          frequency: this.eventRecurrence,
          byDay: [this.dayNamefromDate.substring(0,2).toUpperCase()],
          interval: 1
        };
      } else if(this.eventRecurrence && this.eventRecurrence === 'MONTHLY') {
        this.event.recurrence = {
          frequency: this.eventRecurrence,
          byMonthDay: [this.dayNumberFromDate],
        };
      } else if(this.eventRecurrence && this.eventRecurrence === 'YEARLY') {
        this.event.recurrence = {
          frequency: this.eventRecurrence,
          byYearDay: [this.dayNumberFromDate],
          byMonth: [this.monthFromDate],
        };
      } else {
        this.event.recurrence = null;
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
      if (!this.event.summary) {
        this.fieldError = this.$t('agenda.message.missingEventTitle');
        this.error = true;
        window.setTimeout(() => {
          this.error = null;
        }, 5000);
        return false;
      } else if (this.event.summary.length < 5 || this.event.summary.length > 1024) {
        this.fieldError =  this.$t('agenda.message.missingLengthEventTitle');
        this.error = true;
        window.setTimeout(() => {
          this.error = null;
        }, 5000);
        return false;
      } else if (this.calendarOwner === null) {
        this.fieldError = this.$t('agenda.message.missingSpaceName');
        this.error = true;
        window.setTimeout(() => {
          this.error = null;
        }, 5000);
        return false;
      } else if (!this.event.location) {
        this.fieldError = this.$t('agenda.message.missingEventLocation');
        this.error = true;
        window.setTimeout(() => {
          this.error = null;
        }, 5000);
        return false;
      } else {
        this.error = null;
        return true;
      }
    },
    reset() {
      if (this.event.id) { // In case of edit existing event
        this.eventRecurrence = 'DAILY';
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
        if (this.currentSpace) {
          this.calendarOwner = {
            id: `space:${this.currentSpace.prettyName}`,
            remoteId: this.currentSpace.prettyName,
            providerId: 'space',
            profile: {
              avatarUrl: this.currentSpace.avatarUrl,
              fullName: this.currentSpace.displayName,
            },
          };
          this.$refs.calendarOwner.items = [this.calendarOwner];
        } else {
          this.$refs.calendarOwner.items = [];
          this.calendarOwner = {};
        }
        this.eventRecurrence = 'NO REPEAT';

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
  }
};
</script>