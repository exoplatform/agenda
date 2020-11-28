<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        <div>
          {{ $t('agenda.connectYourPersonalAgenda') }}
        </div>
      </v-list-item-title>
      <v-list-item-subtitle class="my-3 text-sub-title font-italic">
        <agenda-connector-status
          :connected-account="connectedAccount">
          <template slot="connectButton">
            {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
          </template>
        </agenda-connector-status>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        small
        icon
        @click="openDrawer">
        <i class="uiIconEdit uiIconLightBlue pb-2"></i>
      </v-btn>
    </v-list-item-action>
    <agenda-user-connected-account-drawer
      :connected-account="connectedAccount"
      :connectors="connectors" />
    <agenda-connector :connectors="connectors" />
  </v-list-item>
</template>

<script>
export default {
  data: () => ({
    connectedAccount: {},
    connectors: [],
  }),
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.refresh);
    this.refresh();
    this.$root.$on('agenda-connector-loaded', connectors => {
      this.connectors = connectors;
    });
  },
  methods: {
    refresh() {
      this.$calendarService.getAgendaConnectorsSettings().then(connectorSettings => {
        if (connectorSettings && connectorSettings.value) {
          this.connectedAccount = JSON.parse(connectorSettings.value);
        }
        this.$nextTick().then(() => this.$root.$emit('application-loaded'));
      });
    },
    openDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>