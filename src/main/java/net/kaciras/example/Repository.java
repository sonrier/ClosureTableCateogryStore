package net.kaciras.example;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.exceptions.PersistenceException;

import java.util.List;

/**
 * 分类存储，提供对分类的增删改查等操作的支持。
 *
 * 类别（分类）是用于归类、整理文章资源的数据信息。
 * 每个分类都可以拥有若干子分类，但最多只能属于一个父分类。没有父分类的称为顶级分类。
 * 分类的从属关系可以看做一棵多叉数。
 *
 * 除了相互之间的关系外，分类拥有ID、名称、简介、封面四个属性。其中ID为int，
 * 由数据库自动生成。
 *
 * 分类树的根节点成为顶级分类，其ID为0，不可修改、移动、删除或查询其属性，
 * 也不会出现在批量查询的结果集中。
 * 顶级分类仅作为对一些与继承关系有关的参数，例如addNew方法中parent参数为0表示添
 * 加为一级分类。
 *
 * @author Kaciras
 */
@RequiredArgsConstructor
public class Repository {

	private final CategoryMapper categoryMapper;

	/**
	 * 根据指定的id，获取分类的全部属性。
	 *
	 * @param id 分类id
	 * @return 分类的实体对象
	 * @throws IllegalArgumentException 如果id不是正数
	 */
	public Category get(int id) {
		Utils.checkPositive(id, "id");
		return categoryMapper.selectAttributes(id);
	}

	/**
	 * 获取所有分类的数量
	 * @return 数量
	 */
	public int getCount() {
		return categoryMapper.selectCount();
	}

	/**
	 * 获取某一级分类的数量
	 * @param layer 层级（从1开始）
	 * @return 数量
	 * @throws IllegalArgumentException 如果layer不是正数
	 */
	public int getCount(int layer) {
		Utils.checkPositive(layer, "layer");
		return categoryMapper.selectCountByLayer(layer);
	}

	/**
	 * 获取指定id的分类下的直属子分类，id为0表示获取所有一级分类。
	 *
	 * @param id 指定分类的id
	 * @return 直属子类列表，如果id所指定的分类不存在、或没有符合条件的分类，则返回空列表
	 * @throws IllegalArgumentException 如果id小于0
	 */
	public List<Category> findChildren(int id) {
		return findChildren(id, 1);
	}

	/**
	 * 获取指定id的分类下的第n级子分类，id参数可以为0。
	 *
	 * @param id 指定分类的id
	 * @param n 向下级数，1表示直属子分类
	 * @return 子类列表，如果id所指定的分类不存在、或没有符合条件的分类，则返回空列表
	 * @throws IllegalArgumentException 如果id小于0，或n不是正数
	 */
	public List<Category> findChildren(int id, int n) {
		Utils.checkNotNegative(id, "id");
		Utils.checkPositive(n, "n");
		return categoryMapper.selectSubLayer(id, n);
	}

	public List<Category> findByAncestor(int ancestor) {
		Utils.checkNotNegative(ancestor, "ancestor");
		return categoryMapper.selectDescendant(ancestor);
	}

	/**
	 * 新增一个分类，其ID属性将自动生成或计算，并返回。
	 * 新增分类的继承关系由parent属性指定，parent为0表示该分类为一级分类。
	 *
	 * @param category 分类实体对象
	 * @param parent 上级分类id
	 * @return 自动生成的id
	 * @throws IllegalArgumentException 如果parent所指定的分类不存在、category为null或category中存在属性为null
	 */
	public int add(Category category, int parent) {
		Utils.checkNotNegative(parent, "parent");
		if(parent > 0 && categoryMapper.contains(parent) == null) {
			throw new IllegalArgumentException("指定的上级分类不存在");
		}
		try {
			categoryMapper.insert(category);
			categoryMapper.insertPath(category.getId(), parent);
			categoryMapper.insertNode(category.getId());
		} catch (PersistenceException ex) {
			throw new IllegalArgumentException(ex);
		}
		return category.getId();
	}


	/**
	 * 该方法仅更新分类的属性，不修改继承关系，若要移动节点请使用
	 * <code>moveTo</code>和<code>moveTreeTo</code>
	 *
	 * @param category 新的分类信息对象
	 */
	public void update(Category category) {
		try {
			Utils.checkEffective(categoryMapper.update(category));
		}catch (PersistenceException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	/**
	 * 删除一个分类，原来在该分类下的子分类将被移动到该分类的父分类中，
	 * 如果此分类是一级分类，则删除后子分类将全部成为一级分类。
	 *
	 * @param id 要删除的分类的id
	 * @throws IllegalArgumentException 如果指定id的分类不存在
	 */
	public void delete(int id) {
		if(categoryMapper.contains(id) == null) {
			throw new IllegalArgumentException("指定的分类不存在");
		}
		Integer parent = categoryMapper.selectAncestor(id, 1);
		if (parent == null) {
			parent = 0;
		}
		get(id).moveSubTree(parent);
		deleteBoth(id);
	}

	/**
	 * 删除一个分类及其子分类。
	 *
	 * @param id 要删除的分类的id
	 * @throws IllegalArgumentException 如果指定id的分类不存在
	 */
	public void deleteTree(int id) {
		if(categoryMapper.contains(id) == null) {
			throw new IllegalArgumentException("指定的分类不存在");
		}
		deleteBoth(id);
		for (int des : categoryMapper.selectDescendantId(id)) {
			deleteBoth(des);
		}
	}

	/**
	 * 删除一个分类，两个表中的相关记录都删除
	 *
	 * @param id 分类id
	 */
	private void deleteBoth(int id) {
		categoryMapper.delete(id);
		categoryMapper.deletePath(id);
	}
}
