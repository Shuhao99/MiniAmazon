"""amazon URL Configuration

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/4.1/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path, include
from amazon_server.views import login_view, logout_view, register_view, become_seller, add_item, home, browse_view, search_view, purchase_view, multi_purchase_view, user_orders, shopping_cart_view, shopping_cart_multi_purchase_view, place_order_failed_view, delivered_items, comment_form, item_detail_view, single_purchase_view, add_to_cart, remove_from_cart, shopping_cart_update
urlpatterns = [
    path("", home, name="home"),
    path('admin/', admin.site.urls),
    path("login/", login_view, name="login"),
    path("logout/", logout_view, name="logout"),
    path("register/", register_view, name="register"),
    path("become_seller/", become_seller, name="become_seller"),
    path("add_item/", add_item, name="add_item"),
    path('browse/', browse_view, name='browse'),
    path('search/', search_view, name='search'),
    path('purchase/<int:item_id>/', purchase_view, name='purchase'),
    path('multi_purchase/', multi_purchase_view,
         name='multi_purchase'),
    path('user_orders/', user_orders, name='user_orders'),
    path('shopping_cart/', shopping_cart_view, name='shopping_cart'),
    path('shopping_cart_multi_purchase', shopping_cart_multi_purchase_view, name='shopping_cart_multi_purchase'),
    path('place_order_failed', place_order_failed_view, name='place_order_failed'),
    path('delivered_items/', delivered_items, name='delivered_items'),
    path('delivered_items/<int:ordered_item_id>/comment', comment_form, name='comment_form'),
    path('', include('django.contrib.auth.urls')),
    path('item/<int:item_id>/', item_detail_view, name='item_detail'),
    path('item/<int:item_id>/purchase/', single_purchase_view, name='single_purchase'),
    path('add_to_cart/<int:item_id>/', add_to_cart, name='add_to_cart'),
    path('remove_from_cart/<int:item_id>/', remove_from_cart, name='remove_from_cart'),
    path('shopping_cart_update/<int:item_id>/', shopping_cart_update, name='shopping_cart_update'),
]

