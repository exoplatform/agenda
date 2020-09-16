<template>
  <exo-drawer
    ref="UserSettingAgendaDrawer"
    class="UserSettingAgendaDrawer"
    body-classes="hide-scroll decrease-z-index-more"
    right>
    <template slot="title">
      {{ $t('agenda.settings.drawer.title') }}
    </template>
    <template slot="content">
      <div class="valueTab">
        <v-layout class="ma-5 d-flex flex-column">
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.DefaultView') }}:</label>
            <select v-model="value.agendaDefaultView" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option value="day">{{ $t('agenda.label.viewDay') }}</option>
              <option value="week">{{ $t('agenda.label.viewWeek') }}</option>
              <option value="month">{{ $t('agenda.label.viewMonth') }}</option>
            </select>
          </div>
          <div class="d-flex flex-column mb-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.WeekStartOn') }}:</label>
            <select v-model="value.agendaWeekStartOn" class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option value="1,2,3,4,5,6,0">{{ $t('agenda.settings.drawer.day.monday') }}</option>
              <option value="2,3,4,5,6,0,1">{{ $t('agenda.settings.drawer.day.tuesday') }}</option>
              <option value="3,4,5,6,0,1,2">{{ $t('agenda.settings.drawer.day.wednesday') }}</option>
              <option value="4,5,6,0,1,2,3">{{ $t('agenda.settings.drawer.day.thursday') }}</option>
              <option value="5,6,0,1,2,3,4">{{ $t('agenda.settings.drawer.day.friday') }}</option>
              <option value="6,0,1,2,3,4,5">{{ $t('agenda.settings.drawer.day.saturday') }}</option>
              <option value="0,1,2,3,4,5,6">{{ $t('agenda.settings.drawer.day.sunday') }}</option>
            </select>
          </div>

          <div class="d-flex flex-row mt-5">
            <label class="switch-label-text mt-1 text-subtitle-1">{{ $t('agenda.settings.drawer.label.showWorkingTime') }}:</label>
            <v-switch v-model="value.showWorkingTime" class="mt-0 ml-4" />
          </div>
          <div class="d-flex flex-row align-baseline">
            <time-picker v-model="value.workingTimeStart" :disabled="!value.showWorkingTime" />
            <label class="switch-label-text mx-5 text-subtitle-1" :class="{'disabled': !value.showWorkingTime}">{{ $t('agenda.settings.drawer.label.to') }}</label>
            <time-picker v-model="value.workingTimeEnd" :disabled="!value.showWorkingTime" />
          </div>
        </v-layout>
      </div>
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-spacer />
        <v-btn
          class="btn mr-2"
          @click="close">
          <template>
            {{ $t('agenda.settings.drawer.button.cancel') }}
          </template>
        </v-btn>
        <v-btn
          class="btn btn-primary"
          @click="save">
          {{ $t('agenda.settings.drawer.button.save') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    value: {
      type: Object,
      default: () => ({}),
    },
  },
  created() {
    this.$root.$on('user-settings-agenda-drawer-open', this.open);
  },
  methods: {
    open() {
      this.$refs.UserSettingAgendaDrawer.open();
    },
    close() {
      this.$refs.UserSettingAgendaDrawer.close();
    },
    save() {
      this.$refs.UserSettingAgendaDrawer.startLoading();
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/USER,${eXo.env.portal.userName}/APPLICATION,Agenda/agendaSettings`, {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          value: JSON.stringify(this.value),
        }),
      }).then(resp => {
        if (resp && resp.ok) {
          this.$refs.UserSettingAgendaDrawer.close();
        }
      })
        .then(() => {
          window.location.replace(window.location.href);
        })
        .finally(() => {
          this.$refs.UserSettingAgendaDrawer.endLoading();
        });
    },
  },
};
</script>

