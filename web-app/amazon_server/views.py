from django.shortcuts import render, redirect
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.forms import AuthenticationForm
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from .forms import BecomeSellerForm, AddItemForm, MultiPurchaseForm, ConfirmOrderForm
from .models import Ordered_Items, UserProfile, Item, ShoppingCartItem
from django.core.exceptions import ObjectDoesNotExist
from django.shortcuts import get_object_or_404
from .models import WareHouse, Order, Ordered_Items
from .forms import PurchaseForm
from django.db.models import Func
from django.db import models
from django.db import transaction
from .models import WareHouse
import socket
from django.http import HttpResponseRedirect
import time

def send_order_to_daemon(order_id):
    daemon_ip = 'vcm-32288.vm.duke.edu'
    daemon_port = 8888
    timeout = 5
    start_time = time.time()

    while True:
        elapsed_time = time.time() - start_time
        if elapsed_time > timeout:
            break

        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client:
            try:
                client.settimeout(timeout - elapsed_time)
                client.connect((daemon_ip, daemon_port))
                msg = str(order_id) + '\n'
                client.send(msg.encode('utf-8'))
                data = client.recv(1024)
                data = data.decode()
                res = data.split(":")

                if res[0] == "ack" and res[1].strip() == str(order_id):
                    return True

            except socket.timeout:
                break
            except Exception as e:
                print(f"Error in socket connection: {e}")
                break

    return False

def create_warehouses_if_needed():
    if WareHouse.objects.count() == 0:
        with transaction.atomic():
            for x, y in zip(range(10, 110, 10), range(10, 110, 10)):
                WareHouse.objects.create(x_cord=x, y_cord=y)

class HaversineDistance(Func):
    function = 'ST_Distance_Sphere'
    output_field = models.FloatField()

    def __init__(self, x_cord, y_cord, **extra):
        super().__init__([models.Value(x_cord), models.Value(y_cord)], **extra)

def home(request):
    # create_warehouses_if_needed()
    return render(request, "amazon_server/home.html")

def login_view(request):
    if request.method == "POST":
        form = AuthenticationForm(request, data=request.POST)
        username = request.POST["username"]
        password = request.POST["password"]
        user = authenticate(request, username=username, password=password)
        if user is not None:
            login(request, user)
            return redirect("home")
        else:
            messages.error(request, "Invalid username or password.")
    else:
        form = AuthenticationForm(request)
    return render(request, "amazon_server/login.html", {"form": form})

def logout_view(request):
    logout(request)
    return redirect("login")

def register_view(request):
    if request.method == "POST":
        form = UserCreationForm(request.POST)
        if form.is_valid():
            user = form.save()
            username = form.cleaned_data.get("username")
            messages.success(request, f"Account created for {username}.")
            user_profile = UserProfile(user=user, is_seller=False)
            user_profile.save()
            return redirect("login")
    else:
        form = UserCreationForm()
    return render(request, "amazon_server/register.html", {"form": form})

@login_required
def become_seller(request):
    user_profile = UserProfile.objects.get(user=request.user)
    if request.method == "POST":
        form = BecomeSellerForm(request.POST)
        if form.is_valid():
            user_profile.is_seller = True
            user_profile.save()
            messages.success(request, "You are now a seller.")
            return redirect("home")
    else:
        form = BecomeSellerForm()
    return render(request, "amazon_server/become_seller.html", {"form": form, "user_profile": user_profile})

@login_required
def add_item(request):
    user_profile = UserProfile.objects.get(user=request.user)
    if request.method == "POST":
        form = AddItemForm(request.POST)
        if form.is_valid():
            description = form.cleaned_data.get("description")
            if Item.objects.filter(description=description).exists():
                messages.error(request, "This item has already been registered.")
            else:
                Item.objects.create(description=description)
                messages.success(request, "Item added successfully.")
                return redirect("home")
    else:
        form = AddItemForm()
    return render(request, "amazon_server/add_item.html", {"form": form, "user_profile": user_profile})

@login_required
def browse_view(request):
    items = Item.objects.all()
    return render(request, 'amazon_server/browse.html', {'items': items})

@login_required
def place_order_failed_view(request):
    return render(request, 'amazon_server/place_order_failed.html')

@login_required
def search_view(request):
    query = request.GET.get('q', '')
    if query:
        items = Item.objects.filter(description__icontains=query)
    else:
        items = None
    return render(request, 'amazon_server/search.html', {'items': items, 'query': query})

@login_required
def purchase_view(request, item_id):
    item = get_object_or_404(Item, id=item_id)

    if request.method == 'POST':
        form = PurchaseForm(request.POST)
        if form.is_valid():
            x_cord = form.cleaned_data['x_cord']
            y_cord = form.cleaned_data['y_cord']
            count = form.cleaned_data['count']

            # Find the nearest warehouse
            nearest_warehouse = WareHouse.objects.annotate(
                distance=models.ExpressionWrapper(
                    models.F('x_cord') - x_cord, output_field=models.IntegerField()
                ) ** 2 + models.ExpressionWrapper(
                    models.F('y_cord') - y_cord, output_field=models.IntegerField()
                ) ** 2
            ).order_by('distance').first()

            # Create an order with the nearest warehouse
            order = Order.objects.create(item=item, count=count, warehouse=nearest_warehouse)
            # success = send_order_to_daemon(order.order_id)
            if True:
                messages.success(request, 'Order placed successfully.')
            else:
                messages.error(request, 'Order failed to be placed.')
                order.delete()  # Remove the order from the database if the acknowledgment is not received
            return redirect('browse')
    else:
        form = PurchaseForm()

    return render(request, 'amazon_server/purchase.html', {'item': item, 'form': form})

@login_required
def multi_purchase_view(request):
    selected_items = request.GET.getlist('selected_items')
    item_counts = {item_id: int(request.GET[f'count_{item_id}']) for item_id in selected_items}
    
    if request.method == 'POST':
        form = ConfirmOrderForm(request.POST)
        if form.is_valid():
            # Create order and ordered_items
            x_cord = form.cleaned_data['x_cord']
            y_cord = form.cleaned_data['y_cord']
            ups_account_id = form.cleaned_data['ups_account_id']  # Extract UPS Account Id
            warehouse = WareHouse.objects.all().order_by(   # Get the closest warehouse
                models.ExpressionWrapper(
                    models.F('x_cord') - x_cord,
                    output_field=models.IntegerField()
                )**2 +
                models.ExpressionWrapper(
                    models.F('y_cord') - y_cord,
                    output_field=models.IntegerField()
                )**2
            ).first()

            buyer = request.user
            order = Order.objects.create(warehouse=warehouse, buyer=buyer, status='Opened', dest_x=x_cord, dest_y=y_cord, ups_account_name=ups_account_id or None)
            for item_id, count in item_counts.items():
                item = Item.objects.get(id=item_id)
                Ordered_Items.objects.create(item=item, count=count, order=order)
            success = send_order_to_daemon(order.order_id)
            if success:
                return redirect('home')
            else:
                order.delete()
                return redirect('place_order_failed')  # need a order place failed page
    else:
        form = ConfirmOrderForm()
    selected_items_with_quantities = {
        int(item_id): {
            'quantity': int(request.GET[f'count_{item_id}']),
            'item': Item.objects.get(id=item_id),
        }
        for item_id in request.GET.getlist('selected_items')
    }
    context = {
        'form': form,
        'selected_items': selected_items_with_quantities,
    }
    return render(request, 'amazon_server/multi_purchase.html', context)

@login_required
def user_orders(request):
    orders = Order.objects.filter(buyer=request.user)
    rows = []
    for order in orders:
        rows.append("Package id / Tracking Number: " + str(order.order_id))
        rows.append("Warehouse Coordinates: " + str(order.warehouse.x_cord) + ", " + str(order.warehouse.y_cord))
        rows.append("Status: " + order.status) 
        rows.append("Destination Coordinates: " + str(order.dest_x) + ", " + str(order.dest_y))
        if order.ups_account_name:  # Display UPS account name if it exists
            rows.append("UPS Account Name: " + order.ups_account_name)
        if order.package_id:  # Display package id if it exists
            rows.append("Package ID: " + str(order.package_id))
        items = Ordered_Items.objects.filter(order=order)
        for item in items:
            rows.append(item.item.description + " " + "*" + " " + str(item.count)) 
        rows.append("----------------------------------------")
    result = ''
    for i in rows:
        result += i
    return render(request, 'user_orders.html', {'rows': rows, 'result': result})


@login_required
def shopping_cart_view(request):
    if request.method == 'GET':
        selected_items = request.GET.getlist('selected_items')
        shopping_cart_items = []

        for item_id in selected_items:
            item = Item.objects.get(id=item_id)
            quantity = int(request.GET.get('count_{}'.format(item_id)))

            try:
                shopping_cart_item = ShoppingCartItem.objects.get(user=request.user, item=item)
                shopping_cart_item.quantity += quantity
                shopping_cart_item.save()
            except ShoppingCartItem.DoesNotExist:
                shopping_cart_item = ShoppingCartItem.objects.create(user=request.user, item=item, quantity=quantity)
                shopping_cart_items.append(shopping_cart_item)
        shopping_cart_items = ShoppingCartItem.objects.filter(user=request.user)
        context = {
        'shopping_cart_items': shopping_cart_items,}
    if request.method == 'POST':
        # get the list of selected item IDs from the form
        selected_items = request.POST.getlist('selected_items')

        # delete the selected items from the user's shopping cart
        ShoppingCartItem.objects.filter(user=request.user, item__in=selected_items).delete()

        # redirect to the shopping cart page
        return redirect('shopping_cart')

    return render(request, 'shopping_cart.html', context)

@login_required
def shopping_cart_multi_purchase_view(request):
    selected_items = request.GET.getlist('selected_items')
    item_counts = {item_id: int(request.GET[f'count_{item_id}']) for item_id in selected_items}
    
    if request.method == 'POST':
        form = ConfirmOrderForm(request.POST)
        if form.is_valid():
            # Create order and ordered_items
            x_cord = form.cleaned_data['x_cord']
            y_cord = form.cleaned_data['y_cord']
            ups_account_id = form.cleaned_data['ups_account_id']
            warehouse = WareHouse.objects.all().order_by(   # Get the closest warehouse
                models.ExpressionWrapper(
                    models.F('x_cord') - x_cord,
                    output_field=models.IntegerField()
                )**2 +
                models.ExpressionWrapper(
                    models.F('y_cord') - y_cord,
                    output_field=models.IntegerField()
                )**2
            ).first()

            buyer = request.user
            order = Order.objects.create(warehouse=warehouse, buyer=buyer, status='Opened', dest_x=x_cord, dest_y=y_cord, ups_account_name=ups_account_id or None)
            ordered_items = []
            for item_id, count in item_counts.items():
                item = Item.objects.get(id=item_id)
                Ordered_Items.objects.create(item=item, count=count, order=order)
                ordered_items.append((item_id, count))
            success = send_order_to_daemon(order.order_id)
            if success:
                # update shopping cart
                for item_id, count in ordered_items:
                    item = Item.objects.get(id=item_id)
                    shopping_cart_item = ShoppingCartItem.objects.filter(user=request.user, item = item).first()
                    if count < shopping_cart_item.quantity:
                        shopping_cart_item.quantity = shopping_cart_item.quantity - count
                        shopping_cart_item.save()
                    else:
                        shopping_cart_item.delete()
                return redirect('home')
            else:
                order.delete()
                return redirect('place_order_failed')  # need a order place failed page
    else:
        form = ConfirmOrderForm()
    selected_items_with_quantities = {
        int(item_id): {
            'quantity': int(request.GET[f'count_{item_id}']),
            'item': Item.objects.get(id=item_id),
        }
        for item_id in request.GET.getlist('selected_items')
    }
    context = {
        'form': form,
        'selected_items': selected_items_with_quantities,
    }
    return render(request, 'amazon_server/shopping_cart_multi_purchase.html', context)
