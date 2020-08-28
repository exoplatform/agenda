<template>
  <v-container class="event-form">
    <v-layout row>
      <v-flex class="md3">
        <label class="float-left mt-5 mr-2 text-subtitle-1">Create</label>
        <input ref="eventTitle" :placeholder="$t('agenda.eventTitle')" type="text" name="title" class="ignore-vuetify-classes my-3" />
      </v-flex>
      <v-flex class="md3">
        <span class="mt-5 ml-2 text-subtitle-1 font-weight-bold">in</span>
        <input ref="selectInput2" type="text" name="name" class="ignore-vuetify-classes my-3" />
      </v-flex>
    </v-layout>
    <v-layout row class="mt-5">
      <v-flex class="md5">
        <v-layout row>
          <v-icon size="18" class="mr-11">
            fas fa-map-marker-alt
          </v-icon>
          <input ref="locationEvent" :placeholder="$t('agenda.eventLocation')" type="text" name="locationEvent" class="ignore-vuetify-classes my-3" />
        </v-layout>

        <v-layout row class="mt-5">
          <v-icon size="18" class="mr-2">
            fas fa-bell
          </v-icon>
          <label class="mt-5 mr-1">{{ $t('agenda.label.notifyUsers') }}</label>
          <div v-for="notifs in notifications" :key="notifs">
            <input ref="timeNotification" type="text" name="timeNotification" class="ignore-vuetify-classes my-3 time-notification" />
            <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
              <option>Minutes</option>
              <option>Hours</option>
              <option>days</option>
              <option>weeks</option>
            </select>
            <span>{{ $t('agenda.label.beforeStart') }}</span>
            <span>{{ notifs.id }}</span>
            <v-btn
              color="grey"
              icon
              dark
              @click="removeNotifUser(notifs.id)"
            >
              <v-icon>
                mdi-close
              </v-icon>
            </v-btn>
          </div>
        </v-layout>
        <v-layout row class="mt-5">
          <a @click="addNotification">{{ $t('agenda.addNotification') }}</a>
        </v-layout>
        <v-layout row class="mt-5">
          <v-icon size="18" class="mr-11">
            fas fa-redo-alt
          </v-icon>
          <select class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option>1</option>
            <option>2</option>
            <option>3</option>
            <option>4</option>
          </select>
        </v-layout>

        <v-layout row class="mt-5">
          <v-icon size="18" class="mr-12">
            fas fa-file-alt
          </v-icon>
          <textarea ref="autoFocusInput1" :placeholder="$t('agenda.description')" type="text" name="description" rows="20" maxlength="2000" noresize class="ignore-vuetify-classes my-3"></textarea>
        </v-layout>

        <v-layout row class="mt-5">
          <v-icon size="18">
            fas fa-paperclip
          </v-icon>
          <a>{{ $t('agenda.attachFile') }}</a>
        </v-layout>
      </v-flex>
      <v-flex class="md1">
        <v-divider vertical />
      </v-flex>
      <v-flex>
        <v-layout row>
          <v-icon size="18" class="mr-2">
            fas fa-users
          </v-icon>
          <input ref="addParticipants" :placeholder="$t('agenda.addParticipants')" type="text" name="addParticipants" class="ignore-vuetify-classes my-3" />
        </v-layout>
        <v-layout row>
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch v-model="enablePermission" class="mt-0 ml-4" />
        </v-layout>
        <v-layout row class="font-weight-regular">
          {{ $t('agenda.modifyEventPermissionDescription') }}
        </v-layout>
        <v-layout row>
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.enableInvitation') }}</label>
          <v-switch v-model="enableInvitation" class="mt-0 ml-4" />
        </v-layout>
        <v-layout row class="font-weight-regular">
          {{ $t('agenda.enableInvitationDescription') }}
        </v-layout>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script>
export default {
  data() {
    return {
      notifications: [],
      nbNotif: 0,
      enablePermission: false,
      enableInvitation: false
    };
  },
  methods:{
    addNotification() {
      this.notifications.push({
        id: this.nbNotif++
      });
    },
    removeNotifUser(index) {
      this.notifications = this.notifications.filter((n) => n.id !== index);
    }
  }
};
</script>