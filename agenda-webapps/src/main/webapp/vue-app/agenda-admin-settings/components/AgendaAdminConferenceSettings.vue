<template>
  <div class="white rounded-lg ma-5 pl-7">
    <h4 class="py-5 font-weight-bold">
      {{ $t('agenda.agendaConferences') }}
    </h4>
    <v-divider />
    <v-data-table
      :headers="headers"
      :items="conferenceProviders"
      :items-per-page="itemsPerPage"
      :hide-default-footer="hideFooter"
      :no-data-text="$t('agenda.noConferences')"
      :footer-props="{
        itemsPerPageText: `${$t('agenda.itemsPerPage')}:`,
      }"
      disable-sort>
      <template slot="item" slot-scope="props">
        <tr>
          <td>
            <div class="align-center">
              {{ $t(`${props.item.title}`) }}
            </div>
          </td>
          <td>
            <div class="d-flex flex-column align-center">
              <v-switch
                v-model=" props.item.enabled"
                :diabled="loading"
                :loading="loading"
                :ripple="false"
                color="primary"
                @change="enableProvider(props.item)" />
            </div>
          </td>
        </tr>
      </template>
    </v-data-table>
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
    conferenceProviders: [],
    headers: [],
    itemsPerPage : 10,
  }),
  computed: {
    hideFooter() {
      return this.conferenceProviders && this.conferenceProviders.length <= this.itemsPerPage;
    },
  },
  watch: {
    settings() {
      this.refreshConferencesList();
    },
  },
  created() {
    this.headers = [
      { text: this.$t('agenda.name'), align: 'center' },
      { text: this.$t('agenda.active'), align: 'center' }
    ];
  },
  methods: {
    refreshConferencesList() {
      if (!eXo.webConferencing || !eXo.webConferencing.getAllProviders) {
        return;
      }
      eXo.webConferencing.getAllProviders().then(providers => {
        this.conferenceProviders = [];
        if (providers && providers.length) {
          providers.forEach(provider => {
            const conferenceProvider = {
              title: provider.getTitle(),
              type: provider.getType(),
              enabled: false,
            };
            if (this.settings && this.settings.webConferenceProviders && this.settings.webConferenceProviders.length) {
              conferenceProvider.enabled = this.settings.webConferenceProviders.find(webConferenceProviderName => webConferenceProviderName === conferenceProvider.type);
            }
            this.conferenceProviders.push(conferenceProvider);
          });
        }
      });
    },
    enableProvider(provider) {
      this.loading = true;
      this.$settingsService.saveEnabledWebConferencingProvider(provider.type)
        .then(() => this.settings.webConferenceProviders = [provider.type])
        .finally(() => {
          this.loading = false;
          this.refreshConferencesList();
        });
    }
  }
};
</script>
