<template>
  <div v-if="conferenceProvider" class="d-flex flex-row">
    <i class="uiIconVideo darkGreyIcon uiIcon32x32 my-auto mr-11"></i>
    <div class="d-flex flex-row my-auto">
      <template v-if="eventConference">
        <div
          v-if="eventConferenceUrl"
          v-autolinker="eventConferenceUrl"
          class="my-auto"></div>
        <v-chip
          v-else
          color="primary"
          class="my-auto"
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
          class="ml-auto"
          @click="deleteCallURL">
          <v-icon>
            mdi-close
          </v-icon>
        </v-btn>
      </template>
      <v-btn
        v-else
        :loading="loading"
        class="btn btn-primary border-radius my-auto"
        @click="createCallUrl">
        {{ $t('agenda.createEventConference') }}
      </v-btn>
    </div>
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
  },
  data: () => ({
    loading: false,
    conferenceProviders: null,
  }),
  computed:{
    enabledConferenceProviderName() {
      return this.settings
          && this.settings.webConferenceProviders
          && this.settings.webConferenceProviders.length
          && this.settings.webConferenceProviders[0];
    },
    conferenceProvider() {
      return this.conferenceProviders && this.conferenceProviders.find(provider => provider.getType() === this.enabledConferenceProviderName);
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
  mounted() {
    if (eXo && eXo.webConferencing) {
      eXo.webConferencing.getAllProviders().then(providers => {
        // Filter web conferencing providers to allow using
        // only those that supports URL generation
        this.conferenceProviders = providers && providers.filter(provider => provider.isInitialized && provider.getCallId);
      });
    }
  },
  methods:{
    createCallUrl() {
      this.event.conferences = [{
        type: this.enabledConferenceProviderName,
      }];
    },
    deleteCallURL() {
      this.event.conferences = null;
    },
  }
};
</script>
