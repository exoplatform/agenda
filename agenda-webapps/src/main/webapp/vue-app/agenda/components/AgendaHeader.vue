<template>
  <v-app>
    <main>
      <v-layout justify-center>
        <v-flex xs12>
          <v-toolbar
            color="white"
            class="ma-5"
            flat
            dense>
            <create-event-button />
            <v-btn
              class="btn">
              <v-icon dark left>mdi-sync</v-icon>
              {{ $t('agenda.button.syncCalendar') }}
            </v-btn>
            <div class="ml-4">
              <v-tabs v-model="activeView">
                <v-tab
                  dark
                  @click="$root.$emit('switch-type', 'day')">
                  {{ $t('agenda.label.viewDay') }}
                </v-tab>
                <v-tab
                  dark
                  @click="$root.$emit('switch-type', 'week')">
                  {{ $t('agenda.label.viewWeek') }}
                </v-tab>
                <v-tab
                  dark
                  @click="$root.$emit('switch-type', 'month')">
                  {{ $t('agenda.label.viewMonth') }}
                </v-tab>
              </v-tabs>
            </div>
            <div class="uiSearchInput">
              <input
                id="inputValue"
                type="text"
                class="input-large ignore-vuetify-classes"
                :placeholder="$t('agenda.SearchEventPlaceholder')">
            </div>
            <div>
              <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
                <option>My Event</option>
                <option>All Events</option>
              </select>
            </div>
            <v-spacer />
            <v-btn icon>
              <v-icon>mdi-format-list-bulleted</v-icon>
            </v-btn>
            <v-btn icon>
              <v-icon> mdi-settings</v-icon>
            </v-btn>
          </v-toolbar>
        </v-flex>
      </v-layout>
    </main>
  </v-app>
</template>

<script>
export default {
  data: () => ({
    activeView:1
  })
}
</script>