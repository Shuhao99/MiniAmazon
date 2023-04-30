from django.shortcuts import render, redirect
from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.forms import AuthenticationForm
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from .forms import BecomeSellerForm, AddItemForm, MultiPurchaseForm, ConfirmOrderForm, CommentForm, RegisterForm
from .models import Ordered_Items, UserProfile, Item, ShoppingCartItem
from django.core.exceptions import ObjectDoesNotExist
from django.shortcuts import get_object_or_404
from .models import WareHouse, Order, Ordered_Items, Comment
from .forms import PurchaseForm
from django.db.models import Func
from django.db import models
from django.db import transaction
from .models import WareHouse
import socket
from django.http import HttpResponseRedirect
import time
from django.urls import reverse
from django.core.mail import send_mail
from django.shortcuts import get_object_or_404

def send_confirmation(request, order):
    subject = 'Order Confirmation'
    message = f'Dear {request.user.username},\n\nYour order has been placed successfully. Your order ID is {order.order_id}.\n\nThank you for shopping with us!'
    from_email = 'jeremyz0903@gmail.com'  # Use your email address here
    recipient_list = [request.user.email]
    send_mail(subject, message, from_email, recipient_list, fail_silently=False)

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
    if request.method == 'POST':
        user_form = RegisterForm(request.POST)
        if user_form.is_valid():
            user = user_form.save()
            user_profile = UserProfile.objects.create(user=user)
            return redirect('login')
    else:
        user_form = RegisterForm()
    return render(request, 'amazon_server/register.html', context={'form': user_form})

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
        form = AddItemForm(request.POST, request.FILES)
        if form.is_valid():
            description = form.cleaned_data.get("description")
            if Item.objects.filter(description=description, seller=request.user).exists():
                messages.error(request, "This item has already been registered.")
            else:
                header_img = form.cleaned_data['header_img']
                Item.objects.create(description=description, seller=request.user, header_img=header_img)
                messages.success(request, "Item added successfully.")
                return redirect("home")
    else:
        form = AddItemForm()
    return render(request, "amazon_server/add_item.html", {"form": form, "user_profile": user_profile})

@login_required
def browse_view(request):
    items = Item.objects.all()
    comments = Comment.objects.all()
    context = {'items': items, 'comments': comments}
    return render(request, 'amazon_server/browse.html', context)

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
    comments = Comment.objects.all()
    return render(request, 'amazon_server/search.html', {'items': items, 'query': query, 'comments':comments})

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
                send_confirmation(request, order)
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
def single_purchase_view(request, item_id):
    if request.method == 'POST':
        form = ConfirmOrderForm(request.POST)
        if form.is_valid():
            x_cord = form.cleaned_data['x_cord']
            y_cord = form.cleaned_data['y_cord']
            ups_account_id = form.cleaned_data['ups_account_id']
            warehouse = WareHouse.objects.all().order_by(
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

            item = get_object_or_404(Item, id=item_id)
            quantity = int(request.POST.get('quantity', 1))
            Ordered_Items.objects.create(item=item, count=quantity, order=order)

            success = send_order_to_daemon(order.order_id)
            if success:
                messages.success(request, 'Your order has been placed successfully.')
                return redirect('home')
            else:
                order.delete()
                messages.error(request, 'Failed to place your order.')
                return redirect('place_order_failed')

    else:
        form = ConfirmOrderForm()

    context = {
        'form': form,
        'item': get_object_or_404(Item, id=item_id),
    }
    return render(request, 'amazon_server/single_purchase.html', context)


@login_required
def user_orders(request):
    orders = Order.objects.filter(buyer=request.user).order_by("order_id")
    order_items = []

    for order in orders:
        items = Ordered_Items.objects.filter(order=order)
        order_data = {
            'order': order,
            'items': []
        }
        for item in items:
            item_data = {
                'item': item,
                'comment_url': None
            }
            if order.status == 'delivered':
                item_data['comment_url'] = reverse('comment_form', args=[item.pk])
            order_data['items'].append(item_data)
        order_items.append(order_data)

    return render(request, 'user_orders.html', {'order_items': order_items})




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
                send_confirmation(request, order)
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

def delivered_items(request):
    delivered_orders = Order.objects.filter(status='delivered')
    ordered_items = Ordered_Items.objects.filter(order__in=delivered_orders)

    context = {'ordered_items': ordered_items}
    return render(request, 'delivered_items.html', context)

def comment_form(request, ordered_item_id):
    ordered_item = Ordered_Items.objects.get(pk=ordered_item_id)
    if request.method == 'POST':
        form = CommentForm(request.POST)
        if form.is_valid():
            new_comment = form.save(commit=False)
            new_comment.buyer = request.user
            new_comment.item = ordered_item.item
            new_comment.save()
            return redirect('delivered_items')
    else:
        form = CommentForm()
    return render(request, 'comment_form.html', {'form': form, 'ordered_item': ordered_item})


@login_required
def item_detail_view(request, item_id):
    item = get_object_or_404(Item, id=item_id)
    comments = Comment.objects.filter(item=item)
    context = {'item': item, 'comments': comments}
    return render(request, 'amazon_server/item_detail.html', context)

@login_required
def add_to_cart(request, item_id):
    item = get_object_or_404(Item, pk=item_id)
    if request.method == 'GET' and 'quantity' in request.GET:
        quantity = request.GET['quantity']
        try:
            shopping_cart_item = ShoppingCartItem.objects.get(user=request.user, item=item)
            shopping_cart_item.quantity += int(quantity)
            shopping_cart_item.save()
        except ShoppingCartItem.DoesNotExist:
            shopping_cart_item = ShoppingCartItem.objects.create(user=request.user, item=item, quantity=quantity)
            shopping_cart_item.save()
        messages.success(request, 'Item added to cart successfully')
        return redirect('browse')
    else:
        context = {'item': item}
        return render(request, 'amazon_server/add_to_cart.html', context)

@login_required
def remove_from_cart(request, item_id):
    cart_item = ShoppingCartItem.objects.get(user=request.user, item_id=item_id)
    cart_item.delete()
    return redirect('shopping_cart')

@login_required
def shopping_cart_update(request, item_id):
    cart_item = ShoppingCartItem.objects.get(user=request.user, item_id=item_id)

    if 'delete' in request.POST:
        cart_item.delete()
    else:
        new_quantity = int(request.POST[f'count_{item_id}'])
        cart_item.quantity = new_quantity
        cart_item.save()

    return redirect('shopping_cart')

@login_required
def items_sold_by_user(request, user_id):
    items = Item.objects.filter(seller__id=user_id)
    return render(request, 'items_sold_by_user.html', {'items': items})