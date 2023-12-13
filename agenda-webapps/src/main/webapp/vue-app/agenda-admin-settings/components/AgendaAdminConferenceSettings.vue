<template>
  <div v-if="isWebConferencingEnabled" class="white">
    <h4 class="pt-5 font-weight-bold">
      {{ $t('agenda.agendaConferences') }}
    </h4>
    <div class="d-flex flex-row">
      <select
        v-model="selectedProviderType"
        class="subtitle-1 ignore-vuetify-classes my-4 me-2"
        @change="saveSelectedProvider">
        <option
          v-for="conferenceProvider in webConferenceProviderChoices"
          :key="conferenceProvider.type"
          :value="conferenceProvider.type">
          {{ $t(conferenceProvider.title) }}
        </option>
      </select>
      <v-progress-circular
        v-if="loading"
        indeterminate
        color="primary"
        size="20"
        class="ms-3 my-auto" />
    </div>
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    loading: false,
    isWebConferencingEnabled: false,
    emptyConferenceProvider: {
      type: '',
      title: 'agenda.emptyWebConferenceProvider',
    },
    conferenceProviders: [],
  }),
  computed: {
    webConferenceProviderChoices() {
      return [this.emptyConferenceProvider, ...this.conferenceProviders];
    },
  },
  watch: {
    settings() {
      this.refreshConferencesList();
    },
  },
  mounted() {
    this.$webConferencingService.checkWebConferencingEnabled().then(enabled => {
      this.isWebConferencingEnabled = enabled;
    });
  },
  methods: {
    refreshConferencesList() {
      return this.$webConferencingService.getAllProviders().then(providers => {
        this.conferenceProviders = [];
        if (providers && providers.length) {
          providers.forEach(provider => {
            // Filter web conferencing providers to allow using
            // only those that supports URL generation
            // and that  are enabled
            if (!provider.isInitialized || !provider.linkSupported && !provider.groupSupported) {
              return;
            }

            const conferenceProvider = {
              title: provider.getTitle(),
              type: provider.getType(),
              enabled: false,
            };
            if (this.settings && this.settings.webConferenceProviders && this.settings.webConferenceProviders.length) {
              conferenceProvider.enabled = this.settings.webConferenceProviders.find(webConferenceProviderName => webConferenceProviderName === conferenceProvider.type);
              if (conferenceProvider.enabled) {
                this.selectedProviderType = conferenceProvider.type;
              }
            }
            this.conferenceProviders.push(conferenceProvider);
          });
        }
        if (!this.selectedProviderType) {
          this.selectedProviderType = '';
        }
      });
    },
    saveSelectedProvider() {
      this.loading = true;
      this.$settingsService.saveEnabledWebConferencingProvider(this.selectedProviderType)
        .then(() => this.settings.webConferenceProviders = [this.selectedProviderType])
        .then(this.refreshConferencesList)
        .finally(() => {
          window.setTimeout(() => {
            this.loading = false;
            this.$root.$emit('alert-message', this.$t('agenda.webConferencingProviderSaved'), 'success');
          },200);
        });
    }
  }
};
</script>
