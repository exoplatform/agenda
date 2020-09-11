<template>
  <exo-drawer
    ref="agendaSettingsDrawer"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.agendaPreferences') }}
    </template>
    <template slot="content">
      <v-tabs
        v-model="settingTab"
        fixed-tabs
        centered
        class="pa-4 text-capitalize">
        <v-tabs-slider />
        <v-tab href="#agendas" dark>
          {{ $t('agenda.agendas') }}
        </v-tab>
        <v-tab href="#settings" dark>
          {{ $t('agenda.settings') }}
        </v-tab>
        <v-tab href="#sync" dark>
          {{ $t('agenda.sync') }}
        </v-tab>
        <v-tab-item value="agendas">
          <agenda-calendars-tab
            ref="calendarsTab"
            @start-loading="$refs.agendaSettingsDrawer.startLoading()"
            @end-loading="$refs.agendaSettingsDrawer.endLoading()" />
        </v-tab-item>
        <v-tab-item value="settings">
          <agenda-settings-tab />
        </v-tab-item>
        <v-tab-item value="sync">
          <agenda-sync-tab />
        </v-tab-item>
      </v-tabs>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data: () => ({
    drawer: false,
    settingTab: null,
  }),
  computed: {
  },
  watch: {
    drawer() {
      if (this.drawer) {
        if (this.$refs.calendarsTab) {
          this.$refs.calendarsTab.reset();
        }
      }
    },
  },
  created() {
    this.$root.$on('agenda-settings-drawer-open', this.open);
  },
  methods: {
    close() {
      this.$refs.agendaSettingsDrawer.close();
    },
    open() {
      this.$refs.agendaSettingsDrawer.open();
    },
  },
};
</script>