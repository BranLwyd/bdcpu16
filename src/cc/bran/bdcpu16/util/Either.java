package cc.bran.bdcpu16.util;

/**
 * Represents a reference that is either one of two types. 
 * @author Brandon Pitman
 *
 * @param <U> the first type this reference can take on
 * @param <V> the second type this reference can take on
 */
public class Either<U, V>
{
	private U left;
	private V right;
	
	/**
	 * Determines if this instance is a left either. Note that an either that contains null is considered neither left nor right.
	 * @return true if and only if this instance contains a non-null left value
	 */
	public boolean isLeft()
	{
		return (this.left != null);
	}
	
	/**
	 * Gets the left value, or null if this is a right either. 
	 * @return the left value or null
	 */
	public U left()
	{
		return left;
	}
	
	/**
	 * Sets the left value.
	 * @param value the value to set this either to
	 */
	public void left(U value)
	{
		this.left = value;
		this.right = null;
	}
	
	/**
	 * Determines if this is a right either. Note that an either that contains null is considered neither left nor right.
	 * @return true if and only if this instance contains a non-null right value
	 */
	public boolean isRight()
	{
		return (this.right != null);
	}
	
	/**
	 * Gets the right value, or null if this is a left either.
	 * @return the right value or null
	 */
	public V right()
	{
		return right;
	}
	
	/**
	 * Sets the right value.
	 * @param value the value to set this either to
	 */
	public void right(V value)
	{
		this.left = null;
		this.right = value;
	}
}
