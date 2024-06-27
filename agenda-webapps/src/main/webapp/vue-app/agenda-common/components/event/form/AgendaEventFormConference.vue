<template>
  <div class="d-flex flex-row">
    <v-icon :class="`darkGreyIcon my-auto ${this.iconClass} mt-4`" size="32px" v-if="!isMobile">fa-video</v-icon>
    <template v-if="isConferenceEnabled">
      <template v-if="eventConference">
        <span :class="`${this.marginClass} mx-0 webconference-event-span`" v-if="eventConferenceUrl && this.conferenceProvider.canModifyEventUrl">
          <input
              id="eventCallURL"
              ref="eventCallURL"
              v-model="conferenceURL"
              :placeholder="$t('agenda.webConferenceURL')"
              type="text"
              name="webConferenceEvent"
              class="ignore-vuetify-classes webconference-event-input mb-0 max-width-fit">
          <div class="flex-row grey--text">
            <i class="fas fa-exclamation-triangle primary--text"></i>
            {{ $t('agenda.webConferenceURL.warning') }}
          </div>
        </span>
        <span
          v-else-if="eventConferenceUrl"
          v-autolinker="eventConferenceUrl"
          class="my-auto"></span>
        <v-chip
          v-else
          color="primary"
          :class="`${this.marginClass} my-auto`"
          outlined>
          <span class="primary--text">
            {{ $t('agenda.webConferenceScheduled') }}
          </span>
        </v-chip>
        <v-btn
          :loading="loading"
          :title="$t('agenda.deleteEventConference')"
          color="grey"
          icon
          dark
          :class="`${this.marginClass}`"
          @click="deleteCallURL">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>

      </template>
      <v-btn
        v-else
        :loading="loading"
        :class="`${this.marginClass} btn btn-primary border-radius`"
        @click="createCallUrl">
        {{ $t('agenda.createEventConference') }}
      </v-btn>
    </template>
    <template v-else>
      <span :class="`${this.marginClass} mx-0 webconference-event-span-without-cross`">
      <input
        id="eventCallURL"
        ref="eventCallURL"
        v-model="conferenceURL"
        :placeholder="$t('agenda.webConferenceURL')"
        type="text"
        name="webConferenceEvent"
        class="ignore-vuetify-classes webconference-event-input max-width-fit">
      </span>
    </template>
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    settings: {
      type: Object,
      default: () => null,
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
    conferenceProvider: {
      type: Object,
      default: () => null
    },
    iconClass: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: false,
    conferenceURL: null,
  }),
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
    },
    marginClass() {
      return this.isMobile ? 'my-0' : 'my-3';
    },
    isConferenceEnabled() {
      return this.conferenceProvider && (!this.eventConferenceType || this.conferenceProvider.getType() === this.eventConferenceType || this.eventConferenceType === 'manual');
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
    eventConferenceId() {
      return this.eventConference && this.eventConference.id;
    },
    eventConferenceType() {
      return this.eventConference && this.eventConference.type;
    },
    eventConferenceUrl() {
      return this.eventConference && this.eventConference.url;
    },
  },
  watch: {
    conferenceURL(newVal) {
      if (!newVal) {
        this.$set(this.event, 'conferences', null);
      } else {
        this.$set(this.event, 'conferences', [{
          url: newVal,
          type: 'manual',
        }]);
      }
    }
  },
  mounted() {
    if (this.event && this.event.conferences && this.event.conferences.length) {
      this.conferenceURL = this.event.conferences[0].url;
    } else {
      this.conferenceURL = null;
      this.$set(this.event, 'conferences', null);
    }
  },
  methods: {
    createCallUrl() {
      this.$set(this.event, 'conferences', [{
        type: this.conferenceProvider.getType(),
      }]);

      if (this.conferenceProvider.canModifyEventUrl) {
        this.conferenceProvider.getCallUrl(eXo.env.portal.spaceName).then(url => {
          this.conferenceURL = url;
          this.$set(this.event.conferences[0], 'url', url);
        });
      }
    },
    deleteCallURL() {
      this.$set(this.event, 'conferences', null);
      this.conferenceURL = null;
    },
  }
};
</script>
