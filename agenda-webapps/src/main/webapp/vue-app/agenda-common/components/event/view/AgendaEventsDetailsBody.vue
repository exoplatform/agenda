<template>
  <div class="event-details-body overflow-auto flex-grow-1 d-flex flex-column flex-md-row pa-4 mt-5">
    <div class="flex-grow-1 flex-shrink-0 event-details-body-left " :class="{ 'd-flex' : !$root.isMobile }">
      <div class="full-width" :class="{'mx-auto' : $root.isMobile}">
        <div class="event-date align-center d-flex pb-5">
          <v-icon size="20" class="icon-default-color pe-5">fas fa-calendar-alt</v-icon>
          <div class="d-inline-flex">
            <date-format
              :value="event.startDate"
              :format="fullDateFormat"
              class="me-1" />
            <template v-if="!sameDayDates">
              -
              <date-format
                :value="event.endDate"
                :format="fullDateFormat"
                class="ms-1" />
            </template>
          </div>
        </div>
        <div class="event-time align-center d-flex pb-5">
          <v-icon size="20" class="icon-default-color pe-5">fas fa-clock</v-icon>
          <div class="d-inline-flex">
            <template v-if="event.allDay">
              {{ $t('agenda.allDay') }}
            </template>
            <template v-else>
              <date-format
                :value="event.startDate"
                :format="dateTimeFormat"
                class="me-1" />
              -
              <date-format
                :value="event.endDate"
                :format="dateTimeFormat"
                class="ms-1 me-2" />
            </template>
            <template>
              ( {{ timeZoneName }} )
            </template>
          </div>
        </div>
        <div v-if="hasRecurrence" class="event-recurrence align-center d-flex pb-5 text-truncate">
          <v-icon size="20" class="icon-default-color pe-5">fas fa-redo</v-icon>
          <agenda-event-recurrence :event="event" class="text-wrap text-left" />
        </div>
        <div 
          v-if="canAddReminders"
          class="event-reminders align-center d-flex text-truncate pb-5">
          <v-icon size="20" class="icon-default-color pe-5 mt-2 mb-auto">fas fa-bell</v-icon>
          <v-list
            class="py-0 text-truncate"
            dense>
            <v-list-item v-if="!event.reminders || !event.reminders.length">
              <label class="text-subtitle mx-auto">
                {{ $t('agenda.noRemindersYet') }}
              </label>
            </v-list-item>
            <template v-else>
              <v-list-item
                v-for="(reminder, index) in event.reminders"
                :key="index"
                class="ps-0"
                dense>
                <v-chip
                  class="me-2"
                  color="primary"
                  outlined>
                  <span class="text--primary text-truncate">
                    <template v-if="reminder.before">
                      {{ $t('agenda.label.notifyMeBefore', {0: reminder.before, 1: $t(`agenda.option.${reminder.beforePeriodType.toLowerCase()}s`).toLowerCase()}) }}
                    </template>
                    <template v-else>
                      {{ $t('agenda.label.notifyMeWhenEventStarts') }}
                    </template>
                  </span>
                </v-chip>
              </v-list-item>
            </template>
          </v-list>
          <v-btn
            icon
            transparent
            class="mb-auto border-box-sizing"
            @click="$refs.reminders.open()">
            <v-icon size="20" class="icon-default-color">fas fa-pen</v-icon>
          </v-btn>
        </div>
        <div v-if="isConferenceEnabled" class="event-conference d-flex flex-grow-0 flex-shrink-1 pb-5">
          <v-icon size="20" class="icon-default-color pe-4 align-self-start mt-2">fas fa-video</v-icon>
          <div class="d-flex flex-column flex-shrink-1 flex-grow-1">
            <div class="d-flex align-center">
              <v-btn
                target="_blank"
                class="btn btn-primary border-radius me-2"
                @click="openConferenceLink">
                {{ $t('agenda.button.joinMeeting') }}
              </v-btn>
              <v-btn
                :title="$t('agenda.tooltip.meeting.copyLink')"
                icon
                small
                v-on="on"
                @click="copyConferenceLink">
                <v-icon size="16">fas fa-copy</v-icon>
              </v-btn>
            </div>
            <span class="text-subtitle text-truncate mt-1">{{ eventConferenceUrl }}</span>
          </div>
        </div>
        <div v-if="event.location" class="event-location d-flex flex-grow-0 flex-shrink-1 pb-5">
          <v-icon size="20" class="icon-default-color pe-6">fas fa-map-marker-alt</v-icon>
          <span v-autolinker="event.location" class="align-self-center text-break"></span>
        </div>
        <div v-if="event.description" class="event-description d-flex flex-grow-0 flex-shrink-1 pb-5">
          <v-icon size="20" class="icon-default-color align-self-start mt-2 pe-5">fas fa-align-left</v-icon>
          <span v-sanitized-html="event.description" class="align-self-center text-wrap text-left text-break rich-editor-content pt-1"></span>
        </div>
        <div
          v-if="event.attachments && event.attachments.length !== 0"
          class="event-attachments align-center d-flex pb-5">
          <v-icon size="20" class="icon-default-color pe-5">fas fa-paperclip</v-icon>
          <div
            v-for="attachedFile in event.attachments"
            :key="attachedFile.name"
            class="uploadedFilesItem">
            <div class="showFile">
              <exo-attachment-item :file="attachedFile" />
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="!isRemoteEvent" class="flex-grow-0 mx-5 d-none d-md-block">
      <v-divider vertical class="event-details-body-divider" />
    </div>
    <div v-if="!isRemoteEvent" class="flex-grow-1 flex-shrink-0 d-flex event-details-body-right">
      <div class="mr-1 width-full">
        <agenda-event-attendees
          ref="agendaAttendees"
          :event="event" />
        <div>
          <agenda-connector-contemporary-events
            v-if="enabledconnectors"
            :settings="settings"
            :event="event"
            :connectors="connectors"
            :class="!isAcceptedEvent && 'agenda-hidden-connectors'"
            class="mt-4 mr-auto width-full"
            @download-ics="downloadICS" />
          <agenda-ics
            v-if="addToMyAgenda && !connectedConnector"
            :settings="settings"
            :event="event"
            :connectors="enabledconnectors"
            @download-ics="downloadICS" />
        </div>
      </div>
    </div>
    <agenda-event-reminder-drawer
      v-if="canAddReminders"
      ref="reminders"
      :event="event" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
    connectors: {
      type: Array,
      default: () => []
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
  },
  data() {
    return {
      addToMyAgenda: false,
      fullDateFormat: {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      },
      dateDayFormat: {
        month: 'short',
        day: 'numeric',
      },
      dateTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
      timeZoneName: null,
    };
  },
  computed: {
  /*  bodyElement() {
      return {
        template: this.ExtendedDomPurify.purify(`<div>${this.body}</div>`) || '',
      };
    },*/
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    enabledconnectors() {
      return this.connectors.find(connector => connector.enabled);
    },
    isConferenceEnabled() {
      return this.eventConferenceType === 'manual' || (this.conferenceProvider && this.eventConferenceType && this.conferenceProvider.getType() === this.eventConferenceType);
    },
    eventConferences() {
      return this.event && this.event.conferences;
    },
    eventConference() {
      // For now, use only one web conference per event
      return this.eventConferences
          && this.eventConferences.length
          && this.eventConferences[0];
    },
    eventConferenceType() {
      return this.eventConference && this.eventConference.type;
    },
    eventConferenceUrl() {
      return this.eventConference && this.eventConference.url;
    },
    conferenceHref() {
      if (!this.eventConferenceUrl) { return '#'; }
      return !this.eventConferenceUrl.match(/^(https?:\/\/|\/portal\/)/) ? `//${this.eventConferenceUrl}` : this.eventConferenceUrl;
    },
    calendarOwnerLink() {
      if (this.owner) {
        if (this.owner.providerId === 'organization') {
          return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.owner.remoteId}`;
        } else if (this.owner.providerId === 'space') {
          return `${eXo.env.portal.context}/g/:spaces:${this.owner.remoteId}/`;
        }
      }
      return '';
    },
    hasRecurrence() {
      return this.event.recurrence || this.event.parent && this.event.parent.recurrence;
    },
    isRemoteEvent(){
      return this.event.type === 'remoteEvent';
    },
    isAttendee() {
      return this.event.acl && this.event.acl.attendee;
    },
    isAcceptedEvent() {
      if (!this.isAttendee) {
        return false;
      }
      const currentUserResponse = this.event.attendees.find(attendee => attendee && attendee.identity.remoteId ===  eXo.env.portal.userName);
      return currentUserResponse && currentUserResponse.response !== 'DECLINED';
    },
    owner() {
      return this.event && this.event.calendar && this.event.calendar.owner;
    },
    ownerProfile() {
      return this.owner && (this.owner.profile || this.owner.space);
    },
    ownerAvatarUrl() {
      return this.ownerProfile && (this.ownerProfile.avatar || this.ownerProfile.avatarUrl);
    },
    /**
     * The label of the calendar holding the event: the calendar's
     * user-defined name when it has one — two named personal calendars must
     * be tellable apart — else the owner display name exactly as before.
     *
     * @returns {String} calendar display label
     */
    ownerDisplayName() {
      const calendarName = this.event && this.event.calendar && this.event.calendar.name;
      if (calendarName) {
        return calendarName;
      }
      return this.ownerProfile && (this.ownerProfile.displayName || this.ownerProfile.fullname || this.ownerProfile.fullName);
    },
    sameDayDates() {
      return this.event.startDate && this.event.endDate && this.$agendaUtils.areDatesOnSameDay(this.event.startDate, this.event.endDate);
    },
    canAddReminders() {
      if (!this.isAttendee) {
        return false;
      }
      const currentUserResponse = this.event.attendees.find(attendee => attendee && attendee.identity.remoteId ===  eXo.env.portal.userName);
      return currentUserResponse && (currentUserResponse.response === 'ACCEPTED' || currentUserResponse.response === 'TENTATIVE');
    },
    acceptedResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'ACCEPTED').length;
    },
    refusedResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'DECLINED').length;
    },
    tentativeResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'TENTATIVE').length;
    },
    needsActionResponsesCount() {
      if (!this.event || !this.event.attendees || !this.event.attendees.length) {
        return 0;
      }
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'NEEDS_ACTION').length;
    },
    attendeesResponsesTitle() {
      return this.$t('agenda.attendeesResponsesOverview', {
        0: this.acceptedResponsesCount,
        1: this.refusedResponsesCount,
        2: this.needsActionResponsesCount,
        3: this.tentativeResponsesCount,
      });
    },
  },
  created() {
    this.$featureService.isFeatureEnabled('addToMyAgenda').then(enabled => {this.addToMyAgenda = enabled;});
    this.timeZoneName = this.$agendaUtils.getTimeZoneNameFromTimeZoneId(this.$agendaUtils.USER_TIMEZONE_ID);
  },
  methods: {
    closeDialog() {
      this.$emit('close');
    },
    openConferenceLink() {
      window.open(this.conferenceHref, '_blank');
    },
    copyConferenceLink() {
      navigator.clipboard.writeText(this.eventConferenceUrl).then(() => {
        document.dispatchEvent(new CustomEvent('alert-message', {
          detail: {
            alertType: 'success',
            alertMessage: this.$t('agenda.message.meetingLinkCopied'),
          }
        }));
      });
    },
    async generateICS(event) {
      const formatDate = (date) => {
        return date ? `${new Date(date).toISOString().replace(/[-:]/g, '').split('.')[0]}Z` : '';
      };

      const foldLine = (line) => {
        const maxLength = 70;
        if (line.length <= maxLength) { return line; }
        let result = '';
        while (line.length > maxLength) {
          result += `${line.substring(0, maxLength)}\n`;
          line = ` ${line.substring(maxLength)}`;
        }
        return result + line;
      };

      const confurl = (event.conferences && event.conferences.length > 0) ? event.conferences[0].url : '';
      const htmlDescription = `<html><body>${this.$t('agenda.invitationText')} <b>${event.creator.dataEntity.profile.fullname}</b> ${this.$t('agenda.inSpace')} <b>${event.calendar.title}.</b>\
      ${confurl ? `<br><br><b>${this.$t('agenda.visioLink')}</b> <a href="${confurl}">${confurl}</a>` : ''}\
      ${event.description ? `<br><br><b>${this.$t('agenda.eventDetail')}</b><br>${event.description.replaceAll('\n', '')}</body></html>` : ''}`
        .trim().replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

      const brandingInformation = await this.$brandingService.getBrandingInformation();
      const icsContent = 'BEGIN:VCALENDAR\r\n' +
        'CALSCALE:GREGORIAN\r\n' +
        'METHOD:PUBLISH\r\n' +
        `PRODID:-//${brandingInformation.siteName}//${brandingInformation.companyName}//EN\r\n` +
        'VERSION:2.0\r\n' +
        'BEGIN:VEVENT\r\n' +
        `UID:${event.id || ''}\r\n` +
        `UID:X:${event.id || ''}\r\n` +
        `DTSTAMP:${formatDate(new Date())}\r\n` +
        `DTSTART:${formatDate(event.startDate)}\r\n` +
        `DTEND:${formatDate(event.endDate)}\r\n` +
        `SUMMARY:${event.summary || ''}\r\n` +
        `DESCRIPTION:${htmlDescription || ''}\r\n` +
        `X-ALT-DESC;FMTTYPE=text/html:${htmlDescription}\n` +
        `LOCATION:${event.location || ''}\n` +
        `URL:${confurl}\r\n` +
        `ORGANIZER;CN=${event.creator.dataEntity.profile.fullname}:MAILTO:${event.creator.dataEntity.profile.email}\r\n` +
        'END:VEVENT\r\n' +
        'END:VCALENDAR\r\n';

      const processAndFoldText = (text) => {
        const lines = text.split('\n');
        const foldedLines = lines.map(line => foldLine(line)).join('\n');
        return foldedLines;
      };
      return processAndFoldText(icsContent);
    },
    downloadICS(event) {
      return this.$eventService.generateICS(event.id, this.$agendaUtils.USER_TIMEZONE_ID).then(icsContent => {
        const blob = new Blob([icsContent], { type: 'text/calendar' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `${event.summary}.ics`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      });
    },
  }
};
</script>
