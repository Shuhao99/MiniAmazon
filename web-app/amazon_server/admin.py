from django.contrib import admin
from .models import Order, Item, WareHouse, UserProfile, Ordered_Items
# Register your models here.
admin.site.register(Item)
admin.site.register(WareHouse)
admin.site.register(Order)
admin.site.register(UserProfile)
admin.site.register(Ordered_Items)