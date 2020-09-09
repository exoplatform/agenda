<template>
  <v-card class="event-details">
    <v-toolbar
      flat
      class="event-details-header">
      <v-toolbar-title class="d-flex align-center">
        <span class="event-title px-2 text-truncate">
          {{ event.summary }}
        </span>
        <span class="px-4">
          {{ $t('agenda.details.title') }}
        </span>
        <v-avatar
          v-if="event.calendar.owner.profile"
          size="32"
          class="rounded px-3">
          <v-img :src="event.calendar.owner.profile.avatar" />
        </v-avatar>
        <v-avatar
          v-else-if="event.calendar.owner.space"
          size="32"
          class="rounded px-3">
          <v-img :src="event.calendar.owner.space.avatarUrl" />
        </v-avatar>
        <a href="#" class="text-truncate">{{ event.calendar.title }}</a>
      </v-toolbar-title>
      <v-spacer />
      <v-toolbar-items>
        <v-menu bottom left>
          <template v-slot:activator="{ on, attrs }">
            <v-btn
              icon
              v-bind="attrs"
              v-on="on">
              <v-icon>mdi-dots-vertical</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item
              v-for="(item, i) in items"
              :key="i">
              <v-list-item-title>
                {{ item.title }}
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn
          color="grey"
          icon
          dark
          @click="closeDialog">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </v-toolbar-items>
    </v-toolbar>
    <v-divider />
    <v-container class="event-details-body">
      <v-row class="pa-5">
        <v-col>
          <v-row class="event-date align-center d-flex pb-5">
            <i class="uiIconDatePicker darkGreyIcon uiIcon32x32 pr-5"></i>
            <div v-if="!sameDayDates" class="sameDayDates">
              <div class="d-inline-flex">
                <date-format
                  :value="event.start"
                  :format="dateDayFormat" />
                -<date-format
                  :value="event.end"
                  :format="dateDayFormat" />
                {{ `, ${event.startDate.getFullYear()}` }}
              </div>
            </div>
            <div v-else class="differentDayDates">
              <date-format
                :value="event.start"
                :format="fullDateFormat" />
            </div>
          </v-row>
          <v-row class="event-time align-center d-flex pb-5">
            <i class="uiIconClock darkGreyIcon uiIcon32x32 pr-5"></i>
            <div class="d-inline-flex">
              <date-format
                :value="event.start"
                :format="dateTimeFormat" />
              {{ ' - ' }}
              <date-format
                :value="event.end"
                :format="dateTimeFormat" />
            </div>
          </v-row>
          <v-row class="event-location align-center d-flex pb-5">
            <i class="uiIconLocation darkGreyIcon uiIcon32x32 pr-5"></i>
            <span>{{ event.location }}</span>
          </v-row>
          <v-row class="event-description d-flex pb-5">
            <i class="uiIconDescription darkGreyIcon uiIcon32x32 pr-5"></i>
            <span>{{ event.description }}</span>
          </v-row>
          <v-row
            v-if="event.attachments && event.attachments.length !== 0"
            class="event-attachments align-center d-flex pb-5">
            <i class="uiIconAttach darkGreyIcon uiIcon32x32 pr-5"></i>
            <div
              v-for="attachedFile in event.attachments"
              :key="attachedFile.name"
              class="uploadedFilesItem">
              <div class="showFile"><exo-attachment-item :file="attachedFile" /></div>
            </div>
          </v-row>
          <v-row class="event-external-agendas pb-5">
            <div class="alert alert-info mt-5 rounded-lg">
              <i class="uiIconInformation secondary--text"></i>
              {{ $t('agenda.details.calendar.synchronize') }}
            </div>
            <div class="external-agendas-selector align-center pb-5">
              <i class="uiIconConnect darkGreyIcon uiIcon32x32 pr-5"></i>
              <span>Connect to</span>
              <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
                <option>{{ $t('agenda.details.calendar.google') }}</option>
                <option>{{ $t('agenda.details.calendar.office') }}</option>
                <option>{{ $t('agenda.details.calendar.ics') }}</option>
              </select>
            </div>
          </v-row>
        </v-col>
        <v-col cols="1">
          <v-divider vertical />
        </v-col>
        <agenda-event-attendees :attendees="event.attendees" />
      </v-row>
    </v-container>
  </v-card>
</template>
<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
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
        hour12: false
      }
    };
  },
  computed: {
    sameDayDates() {
      return this.$agendaUtils.areDatesOnSameDay(this.event.startDate, this.event.endDate);
    },
    items() {
      return [
        {title: this.$t('agenda.details.header.menu.edit')},
        {title: this.$t('agenda.details.header.menu.delete')},
        {title: this.$t('agenda.details.header.menu.export')},
      ];
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
      return this.event.attendees.filter(attendee => attendee && attendee.response === 'REFUSED').length;
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
  methods: {
    closeDialog() {
      this.$emit('close');
    },

  }
};
</script>